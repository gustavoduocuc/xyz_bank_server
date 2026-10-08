package cl.duoc.xyzbank.paymentsservice.payments.infrastructure.coreservice;

import cl.duoc.xyzbank.paymentsservice.payments.application.CoreUnavailableException;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingGateway;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingOutcome;
import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A 4xx is core-service's refusal: it is returned, so neither the retry nor the breaker counts it.
 * Unavailability (connection failure, timeout, 5xx) is retried; Retry wraps the breaker, so its
 * fallback runs once retries are exhausted or the circuit is open, and leaves the payment PENDING.
 */
@Component
public class HttpPostingGateway implements PostingGateway {

    private static final Logger LOG = LoggerFactory.getLogger(HttpPostingGateway.class);

    private final RestClient coreServiceClient;

    public HttpPostingGateway(@Qualifier("coreServiceClient") RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    @Retry(name = "coreServicePosting", fallbackMethod = "throwUnavailable")
    @CircuitBreaker(name = "coreService")
    public PostingOutcome post(Payment payment) {
        try {
            coreServiceClient.post()
                    .uri("/internal/postings")
                    .body(new PostingBody(payment.id().toString(), entriesOf(payment)))
                    .retrieve()
                    .toBodilessEntity();
            return PostingOutcome.APPLIED;
        } catch (HttpClientErrorException refusal) {
            LOG.info("core-service refused payment {}: {} {}", payment.id(), refusal.getStatusCode(),
                    refusal.getResponseBodyAsString());
            return PostingOutcome.REFUSED;
        }
    }

    private PostingOutcome throwUnavailable(Payment payment, Exception cause) {
        throw new CoreUnavailableException("core-service could not apply payment " + payment.id(), cause);
    }

    private static List<Entry> entriesOf(Payment payment) {
        List<Entry> entries = new ArrayList<>();
        if (payment.sourceAccountId() != null) {
            entries.add(new Entry(payment.sourceAccountId().toString(), "DEBIT", payment.amount(), payment.currency()));
        }
        if (payment.destinationAccountId() != null) {
            entries.add(new Entry(
                    payment.destinationAccountId().toString(), "CREDIT", payment.amount(), payment.currency()));
        }
        return entries;
    }

    record PostingBody(String paymentId, List<Entry> entries) {
    }

    record Entry(String accountId, String direction, BigDecimal amount, String currency) {
    }
}
