package cl.duoc.xyzbank.bffatm.shared.infrastructure.resilience;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Predicate;

/**
 * What a circuit breaker records as a failure of its dependency: the dependency could not be
 * reached, did not answer in time, or answered 5xx. A 4xx is a correct answer about the request
 * and never opens a circuit (bff-resilience spec; design.md Decision 3).
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return switch (throwable) {
            case CoreServiceCallException failure -> failure.getStatus() >= 500;
            case RestClientResponseException response -> response.getStatusCode().is5xxServerError();
            case ResourceAccessException unreachable -> true;
            default -> false;
        };
    }
}
