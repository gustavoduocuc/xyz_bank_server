package cl.duoc.xyzbank.bffmobile.shared.unit;

import cl.duoc.xyzbank.bffmobile.shared.infrastructure.resilience.DownstreamFailurePredicate;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest.CircuitBreakerClientInterceptor;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The circuit-breaker client interceptor")
class CircuitBreakerClientInterceptorTest {

    /*
     * Cases (design.md Decisions 3 and 7) -- the interceptor runs exactly one exchange inside
     * the breaker; it never retries:
     * 1. A 5xx answer is recorded as a failure and still returned to the caller
     * 2. An IOException (unreachable, timed out) is recorded as a failure and rethrown
     * 3. A 2xx or 4xx answer is recorded as a success
     * 4. An open breaker refuses the call without executing it
     */

    private final AtomicInteger executions = new AtomicInteger();
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerClientInterceptor interceptor;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("authServer", CircuitBreakerConfig.custom()
                .recordException(new DownstreamFailurePredicate())
                .build());
        interceptor = new CircuitBreakerClientInterceptor(circuitBreaker);
    }

    @Test
    @DisplayName("records a 5xx answer as a failure and still returns it")
    void recordsA5xxAnswerAsAFailureAndStillReturnsIt() throws IOException {
        MockClientHttpResponse unavailable = new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE);

        ClientHttpResponse response = interceptor.intercept(request(), new byte[0], answering(unavailable));

        assertSame(unavailable, response);
        assertEquals(1, circuitBreaker.getMetrics().getNumberOfFailedCalls());
        assertEquals(1, executions.get());
    }

    @Test
    @DisplayName("records an IOException as a failure and rethrows it")
    void recordsAnIOExceptionAsAFailureAndRethrowsIt() {
        ClientHttpRequestExecution unreachable = (request, body) -> {
            executions.incrementAndGet();
            throw new IOException("Read timed out");
        };

        assertThrows(IOException.class, () -> interceptor.intercept(request(), new byte[0], unreachable));

        assertEquals(1, circuitBreaker.getMetrics().getNumberOfFailedCalls());
        assertEquals(1, executions.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401})
    @DisplayName("records a 2xx or 4xx answer as a success")
    void recordsA2xxOr4xxAnswerAsASuccess(int status) throws IOException {
        interceptor.intercept(request(), new byte[0],
                answering(new MockClientHttpResponse(new byte[0], HttpStatus.valueOf(status))));

        assertEquals(1, circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        assertEquals(0, circuitBreaker.getMetrics().getNumberOfFailedCalls());
    }

    @Test
    @DisplayName("refuses the call without executing it while the breaker is open")
    void refusesTheCallWithoutExecutingItWhileTheBreakerIsOpen() {
        circuitBreaker.transitionToOpenState();

        assertThrows(CallNotPermittedException.class, () -> interceptor.intercept(
                request(), new byte[0], answering(new MockClientHttpResponse(new byte[0], HttpStatus.OK))));

        assertEquals(0, executions.get());
    }

    private static MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("https://auth-server:9000/oauth2/token"));
    }

    private ClientHttpRequestExecution answering(ClientHttpResponse response) {
        return (request, body) -> {
            executions.incrementAndGet();
            return response;
        };
    }
}
