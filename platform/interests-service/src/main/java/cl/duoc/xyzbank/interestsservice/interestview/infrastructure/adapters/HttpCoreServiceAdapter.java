package cl.duoc.xyzbank.interestsservice.interestview.infrastructure.adapters;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.CoreServiceUnavailableException;
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
import java.util.Map;

@Component
public class HttpCoreServiceAdapter implements CoreServicePort {

    private static final Logger log = LoggerFactory.getLogger(HttpCoreServiceAdapter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient coreServiceClient;
    private final ServiceTokenPort serviceTokenPort;

    public HttpCoreServiceAdapter(RestClient coreServiceClient, ServiceTokenPort serviceTokenPort) {
        this.coreServiceClient = coreServiceClient;
        this.serviceTokenPort = serviceTokenPort;
    }

    @Override
    @CircuitBreaker(name = "coreServiceInterests", fallbackMethod = "fallback")
    @Retry(name = "coreServiceInterests", fallbackMethod = "retryFallback")
    public InterestSummaryResponse fetchInterestSummary(String accountId, String year) {
        log.debug("Fetching interest summary from core-service: accountId={}, year={}", accountId, year);
        try {
            CoreServiceInterestResponse response = coreServiceClient.get()
                    .uri("/internal/accounts/{accountId}/interest-summary?year={year}", accountId, year)
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
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw DomainException.notFound("Interest summary not found for account " + accountId);
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", e);
        }
    }

    @Override
    @CircuitBreaker(name = "coreServiceInterests", fallbackMethod = "balanceFallback")
    @Retry(name = "coreServiceInterests", fallbackMethod = "balanceRetryFallback")
    public AccountBalanceResponse fetchAccountBalance(String accountId) {
        log.debug("Fetching account balance from core-service: accountId={}", accountId);
        String serviceToken = serviceTokenPort.issueServiceToken();
        try {
            CoreServiceBalanceResponse response = coreServiceClient.get()
                    .uri("/internal/accounts/{accountId}/balance", accountId)
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + serviceToken)
                    .retrieve()
                    .body(CoreServiceBalanceResponse.class);
            if (response == null) {
                throw DomainException.notFound("Account balance not found");
            }
            return new AccountBalanceResponse(response.accountId(), response.balance(), response.currency());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw DomainException.notFound("Account not found: " + accountId);
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", e);
        }
    }

    @Override
    @CircuitBreaker(name = "coreServiceInterests", fallbackMethod = "creditFallback")
    @Retry(name = "coreServiceInterests", fallbackMethod = "creditRetryFallback")
    public InterestCreditResponse creditInterest(CreditInterestCommand command) {
        log.debug("Crediting interest via core-service: accountId={}, year={}",
                command.accountId(), command.year());
        String serviceToken = serviceTokenPort.issueServiceToken();
        try {
            CoreServiceInterestCreditResponse response = coreServiceClient.post()
                    .uri("/internal/accounts/{accountId}/interest-credits", command.accountId())
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + serviceToken)
                    .header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey())
                    .body(Map.of(
                            "year", command.year(),
                            "amount", command.amount(),
                            "currency", command.currency(),
                            "interestRate", command.interestRate(),
                            "openingBalance", command.openingBalance(),
                            "closingBalance", command.closingBalance()))
                    .retrieve()
                    .body(CoreServiceInterestCreditResponse.class);
            if (response == null) {
                throw DomainException.create("Empty interest credit response from core-service");
            }
            return new InterestCreditResponse(
                    response.transactionId(),
                    response.accountId(),
                    response.year(),
                    response.amount(),
                    response.currency(),
                    response.occurredOn(),
                    response.newBalance());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw DomainException.notFound("Account not found: " + command.accountId());
            }
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw DomainException.conflict(e.getMessage());
            }
            throw e;
        } catch (ResourceAccessException e) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", e);
        }
    }

    public InterestSummaryResponse retryFallback(String accountId, String year, Throwable throwable) {
        if (throwable instanceof HttpClientErrorException e && e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound("Interest summary not found for account " + accountId);
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(throwable);
    }

    public InterestSummaryResponse fallback(String accountId, String year, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for accountId={}, year={}, reason={}",
                accountId, year, throwable.getMessage());
        return mapSummaryFailure(accountId, throwable);
    }

    public AccountBalanceResponse balanceRetryFallback(String accountId, Throwable throwable) {
        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(throwable);
    }

    public AccountBalanceResponse balanceFallback(String accountId, Throwable throwable) {
        log.warn("Circuit breaker balance fallback for accountId={}, reason={}",
                accountId, throwable.getMessage());
        return mapBalanceFailure(accountId, throwable);
    }

    public InterestCreditResponse creditRetryFallback(CreditInterestCommand command, Throwable throwable) {
        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(throwable);
    }

    public InterestCreditResponse creditFallback(CreditInterestCommand command, Throwable throwable) {
        log.warn("Circuit breaker credit fallback for accountId={}, year={}, reason={}",
                command.accountId(), command.year(), throwable.getMessage());
        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }
        if (throwable instanceof CoreServiceUnavailableException unavailableException) {
            throw unavailableException;
        }
        if (throwable instanceof ResourceAccessException) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", throwable);
        }
        throw new CoreServiceUnavailableException(
                "Unable to credit interest. Please try again later.", throwable);
    }

    private InterestSummaryResponse mapSummaryFailure(String accountId, Throwable throwable) {
        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }
        if (throwable instanceof CoreServiceUnavailableException unavailableException) {
            throw unavailableException;
        }
        if (throwable instanceof HttpClientErrorException e && e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound("Interest summary not found for account " + accountId);
        }
        if (throwable instanceof ResourceAccessException) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", throwable);
        }
        throw new CoreServiceUnavailableException(
                "Unable to fetch interest summary. Please try again later.", throwable);
    }

    private AccountBalanceResponse mapBalanceFailure(String accountId, Throwable throwable) {
        if (throwable instanceof DomainException domainException) {
            throw domainException;
        }
        if (throwable instanceof CoreServiceUnavailableException unavailableException) {
            throw unavailableException;
        }
        if (throwable instanceof HttpClientErrorException e && e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound("Account not found: " + accountId);
        }
        if (throwable instanceof ResourceAccessException) {
            throw new CoreServiceUnavailableException(
                    "Core service is temporarily unavailable. Please try again later.", throwable);
        }
        throw new CoreServiceUnavailableException(
                "Unable to fetch account balance. Please try again later.", throwable);
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

    private record CoreServiceBalanceResponse(
            String accountId,
            BigDecimal balance,
            String currency) {
    }

    private record CoreServiceInterestCreditResponse(
            String transactionId,
            String accountId,
            int year,
            BigDecimal amount,
            String currency,
            String occurredOn,
            BigDecimal newBalance) {
    }
}
