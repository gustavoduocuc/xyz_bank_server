package cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/**
 * Runs exactly one HTTP exchange inside a circuit breaker -- never a retry, which an
 * interceptor could not do anyway (an execution can only be run once). Used for the calls
 * bff-mobile makes from inside Spring Security and Nimbus (code exchange, JWK set) and for
 * refresh and device revocation, none of which may be repeated (add-resilience4j-to-bffs
 * design.md Decisions 1 and 7).
 *
 * <p>Outcomes are recorded as the exceptions the RestClient would raise, so the breaker's
 * failure predicate classifies them like every other downstream call: an IOException as an
 * unreachable dependency, a 5xx answer as a server error; a 2xx or 4xx is a success.
 */
public class CircuitBreakerClientInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerClientInterceptor(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        circuitBreaker.acquirePermission();
        long start = circuitBreaker.getCurrentTimestamp();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException exception) {
            circuitBreaker.onError(elapsedSince(start), circuitBreaker.getTimestampUnit(),
                    new ResourceAccessException(exception.getMessage(), exception));
            throw exception;
        } catch (RuntimeException exception) {
            circuitBreaker.onError(elapsedSince(start), circuitBreaker.getTimestampUnit(), exception);
            throw exception;
        }
        if (response.getStatusCode().is5xxServerError()) {
            circuitBreaker.onError(elapsedSince(start), circuitBreaker.getTimestampUnit(),
                    HttpServerErrorException.create(response.getStatusCode(), "", null, null, null));
        } else {
            circuitBreaker.onSuccess(elapsedSince(start), circuitBreaker.getTimestampUnit());
        }
        return response;
    }

    private long elapsedSince(long start) {
        return circuitBreaker.getCurrentTimestamp() - start;
    }
}
