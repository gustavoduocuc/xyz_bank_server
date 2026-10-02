package cl.duoc.xyzbank.bffweb.shared.unit;

import cl.duoc.xyzbank.bffweb.shared.application.DependencyUnavailableException;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.resilience.DownstreamFailurePredicate;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.resilience.RetryableFailurePredicate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The downstream failure classification")
class ResiliencePredicatesTest {

    /*
     * Cases (design.md Decisions 3 and 4):
     * 1. A 4xx answer is neither a breaker failure nor retryable, whether it arrives raw or as a
     *    CoreServiceCallException
     * 2. A 500 is a breaker failure but is not retried
     * 3. 502, 503 and 504 are both a breaker failure and retryable
     * 4. A connection failure, a timeout, or an answer cut off mid-read (connection reset) is
     *    both a breaker failure and retryable
     * 5. An open circuit is not retried
     * 6. Another dependency being unavailable is neither (it has its own breaker)
     */

    private final DownstreamFailurePredicate isFailure = new DownstreamFailurePredicate();
    private final RetryableFailurePredicate isRetryable = new RetryableFailurePredicate();

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 409, 422})
    @DisplayName("treats a client error as an answer, not a failure")
    void treatsAClientErrorAsAnAnswerNotAFailure(int status) {
        CoreServiceCallException wrapped = new CoreServiceCallException(status, "client error");
        HttpClientErrorException raw = HttpClientErrorException.create(
                HttpStatusCode.valueOf(status), "client error", null, null, null);

        assertFalse(isFailure.test(wrapped));
        assertFalse(isRetryable.test(wrapped));
        assertFalse(isFailure.test(raw));
        assertFalse(isRetryable.test(raw));
    }

    @Test
    @DisplayName("records a 500 as a failure without retrying it")
    void recordsA500AsAFailureWithoutRetryingIt() {
        CoreServiceCallException wrapped = new CoreServiceCallException(500, "server error");

        assertTrue(isFailure.test(wrapped));
        assertFalse(isRetryable.test(wrapped));
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    @DisplayName("records and retries a gateway or unavailability answer")
    void recordsAndRetriesAGatewayOrUnavailabilityAnswer(int status) {
        CoreServiceCallException wrapped = new CoreServiceCallException(status, "unavailable");
        HttpServerErrorException raw = HttpServerErrorException.create(
                HttpStatusCode.valueOf(status), "unavailable", null, null, null);

        assertTrue(isFailure.test(wrapped));
        assertTrue(isRetryable.test(wrapped));
        assertTrue(isFailure.test(raw));
        assertTrue(isRetryable.test(raw));
    }

    @Test
    @DisplayName("records and retries a connection failure or timeout")
    void recordsAndRetriesAConnectionFailureOrTimeout() {
        ResourceAccessException timeout = new ResourceAccessException("Read timed out");

        assertTrue(isFailure.test(timeout));
        assertTrue(isRetryable.test(timeout));
    }

    @Test
    @DisplayName("records and retries an answer cut off by a connection reset")
    void recordsAndRetriesAnAnswerCutOffByAConnectionReset() {
        RestClientException reset = new RestClientException(
                "Error while extracting response", new SocketException("Connection reset"));

        assertTrue(isFailure.test(reset));
        assertTrue(isRetryable.test(reset));
    }

    @Test
    @DisplayName("never retries while the circuit is open")
    void neverRetriesWhileTheCircuitIsOpen() {
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("coreService"));

        assertFalse(isRetryable.test(open));
    }

    @Test
    @DisplayName("leaves another dependency's outage to that dependency's breaker")
    void leavesAnotherDependencysOutageToThatDependencysBreaker() {
        DependencyUnavailableException authServerDown = DependencyUnavailableException.of("Authorization server");

        assertFalse(isFailure.test(authServerDown));
        assertFalse(isRetryable.test(authServerDown));
    }
}
