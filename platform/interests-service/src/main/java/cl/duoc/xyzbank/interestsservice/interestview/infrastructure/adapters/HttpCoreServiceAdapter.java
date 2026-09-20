package cl.duoc.xyzbank.interestsservice.interestview.infrastructure.adapters;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class HttpCoreServiceAdapter implements CoreServicePort {

    private static final Logger log = LoggerFactory.getLogger(HttpCoreServiceAdapter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final RestClient coreServiceClient;

    public HttpCoreServiceAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    @CircuitBreaker(name = "coreServiceInterests", fallbackMethod = "fallback")
    @Retry(name = "coreServiceInterests", fallbackMethod = "retryFallback")
    public InterestSummaryResponse fetchInterestSummary(String accountId, String year, String bearerToken) {
        log.debug("Fetching interest summary from core-service: accountId={}, year={}", accountId, year);
        var request = coreServiceClient.get()
                .uri("/internal/accounts/{accountId}/interest-summary?year={year}", accountId, year);
        if (bearerToken != null && !bearerToken.isBlank()) {
            request = request.header(AUTHORIZATION_HEADER, bearerToken);
        }
        CoreServiceInterestResponse response = request
                .retrieve()
                .body(CoreServiceInterestResponse.class);

        if (response == null) {
            throw DomainException.notFound("Interest summary not found");
        }

        return new InterestSummaryResponse(
                response.accountId(),
                response.year(),
                response.openingBalance(),
                response.closingBalance(),
                response.interestRate(),
                response.interestAmount(),
                response.currency());
    }

    public InterestSummaryResponse retryFallback(
            String accountId, String year, String bearerToken, Throwable throwable) {
        if (throwable instanceof HttpClientErrorException e && e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound("Interest summary not found for account " + accountId);
        }
        throw new RuntimeException(throwable);
    }

    public InterestSummaryResponse fallback(String accountId, String year, String bearerToken, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for accountId={}, year={}, reason={}",
                accountId, year, throwable.getMessage());

        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }

        if (throwable instanceof HttpClientErrorException e && e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound("Interest summary not found for account " + accountId);
        }

        if (throwable instanceof ResourceAccessException) {
            throw DomainException.serviceUnavailable(
                    "Core service is temporarily unavailable. Please try again later.");
        }

        throw DomainException.serviceUnavailable(
                "Unable to fetch interest summary. Please try again later.");
    }

    private record CoreServiceInterestResponse(
            String accountId,
            int year,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal interestRate,
            BigDecimal interestAmount,
            String currency) {
    }
}
