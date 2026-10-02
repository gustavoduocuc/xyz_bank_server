package cl.duoc.xyzbank.bffmobile.shared.infrastructure.resilience;

import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCallException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;
import java.util.function.Predicate;

/**
 * What an idempotent operation may be retried after: the dependency could not be reached, did
 * not answer in time, or answered 502, 503 or 504. Never a 4xx, never a 500 (it would only
 * repeat), and never an open circuit (design.md Decision 4).
 */
public class RetryableFailurePredicate implements Predicate<Throwable> {

    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(502, 503, 504);

    @Override
    public boolean test(Throwable throwable) {
        return switch (throwable) {
            case CoreServiceCallException failure -> TRANSIENT_STATUSES.contains(failure.getStatus());
            case RestClientResponseException response -> TRANSIENT_STATUSES.contains(response.getStatusCode().value());
            // Unreachable, timed out, or the answer was cut off mid-read (as CoreServiceCalls sees it)
            case RestClientException unreachable -> true;
            default -> false;
        };
    }
}
