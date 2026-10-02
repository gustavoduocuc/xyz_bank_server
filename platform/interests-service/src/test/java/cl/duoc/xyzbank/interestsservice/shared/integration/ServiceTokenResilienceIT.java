package cl.duoc.xyzbank.interestsservice.shared.integration;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.shared.domain.ServiceTokenUnavailableException;
import cl.duoc.xyzbank.interestsservice.testsupport.TestAuthServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "resilience4j.retry.instances.authServerToken.waitDuration=10ms")
@DisplayName("interests-service's service token request under failure")
class ServiceTokenResilienceIT {

    /*
     * Cases (interests spec, "interests-service's service token request is retried and protected
     * on its own"):
     * 1. A transient 503 from auth-server is retried and a token is obtained
     * 2. A refused request (401) is sent exactly once and surfaces as the token being unavailable
     * 3. An unreachable auth-server is tried at most 3 times
     */

    @Autowired
    private ServiceTokenPort serviceTokens;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        TestAuthServer.reset();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("retries a transient 503 and obtains a token")
    void retriesATransient503AndObtainsAToken() {
        TestAuthServer.tokenEndpointFailsOnceWith(503);

        String token = serviceTokens.issueServiceToken();

        assertTrue(token.split("\\.").length == 3, token);
        assertEquals(2, TestAuthServer.tokenRequests());
    }

    @Test
    @DisplayName("sends a refused token request exactly once")
    void sendsARefusedTokenRequestExactlyOnce() {
        TestAuthServer.tokenEndpointAnswers(401);

        assertThrows(ServiceTokenUnavailableException.class, () -> serviceTokens.issueServiceToken());

        assertEquals(1, TestAuthServer.tokenRequests());
    }

    @Test
    @DisplayName("tries an unreachable auth-server at most three times")
    void triesAnUnreachableAuthServerAtMostThreeTimes() {
        TestAuthServer.tokenEndpointUnreachable();

        assertThrows(ServiceTokenUnavailableException.class, () -> serviceTokens.issueServiceToken());

        assertEquals(3, TestAuthServer.tokenRequests());
    }
}
