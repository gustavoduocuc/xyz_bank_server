package cl.duoc.xyzbank.interestsservice.shared.infrastructure.resilience;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;
import java.util.function.Predicate;

/**
 * What a service token request may be retried after: the authorization server could not be
 * reached, did not answer in time (or its answer was cut off), or answered 502, 503 or 504.
 * Never a 4xx, never a 500, never an open circuit (add-resilience4j-to-bffs design.md
 * Decision 4).
 */
public class RetryableFailurePredicate implements Predicate<Throwable> {

    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(502, 503, 504);

    @Override
    public boolean test(Throwable throwable) {
        return switch (throwable) {
            case RestClientResponseException response -> TRANSIENT_STATUSES.contains(response.getStatusCode().value());
            case RestClientException unreachable -> true;
            default -> false;
        };
    }
}
