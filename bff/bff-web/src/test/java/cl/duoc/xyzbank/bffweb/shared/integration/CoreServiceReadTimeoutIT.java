package cl.duoc.xyzbank.bffweb.shared.integration;

import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "core-service.read-timeout-ms=300",
        "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms"
})
@DisplayName("bff-web's core-service reads against a slow core-service")
class CoreServiceReadTimeoutIT {

    /*
     * Cases (bff-resilience spec, "Every outbound call is bounded by a timeout"):
     * 1. A read core-service does not answer within the read timeout is abandoned, retried, and
     *    reported unavailable once the timeout plus the permitted retries are exhausted -- well
     *    before core-service's own (much later) answer
     */

    private static final String ACCOUNTS_PATH = "/internal/customers/customer-1/accounts";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
    }

    @DynamicPropertySource
    static void coreService(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @Autowired
    private AccountsPort accounts;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetCoreService() {
        CORE_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopCoreService() {
        CORE_SERVICE.stop();
    }

    @Test
    @DisplayName("gives up on a slow read after the timeout and the permitted retries")
    void givesUpOnASlowReadAfterTheTimeoutAndThePermittedRetries() {
        CORE_SERVICE.stubFor(get(urlPathEqualTo(ACCOUNTS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")
                .withFixedDelay(5_000)));
        Instant start = Instant.now();

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> accounts.fetchAccountsForCustomer("customer-1"));

        Duration elapsed = Duration.between(start, Instant.now());
        assertEquals(503, failure.getStatus());
        CORE_SERVICE.verify(3, getRequestedFor(urlPathEqualTo(ACCOUNTS_PATH)));
        assertTrue(elapsed.compareTo(Duration.ofSeconds(3)) < 0, "took " + elapsed);
    }
}
