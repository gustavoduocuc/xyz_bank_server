package cl.duoc.xyzbank.interestsservice.shared.infrastructure.resilience;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Predicate;

/**
 * What the authorization server's breaker records as a failure: it could not be reached, did not
 * answer in time (or its answer was cut off), or answered 5xx. A 4xx is a correct answer about
 * the request and never opens the circuit (add-resilience4j-to-bffs design.md Decision 3).
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return switch (throwable) {
            case RestClientResponseException response -> response.getStatusCode().is5xxServerError();
            case RestClientException unreachable -> true;
            default -> false;
        };
    }
}
