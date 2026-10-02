package cl.duoc.xyzbank.bffatm.balanceinquiry.integration;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms")
@DisplayName("The balance-inquiry adapter's fault tolerance")
class BalanceInquiryResilienceIT {

    /*
     * Cases (bff-resilience spec, "Only idempotent operations are retried, and never on a
     * client error"):
     * 1. A transient 503 is retried and the balance is returned
     * 2. A 404 is sent exactly once and surfaces unchanged
     * 3. A persistent 503 stops after the configured 3 attempts with core-service unavailable
     */

    private static final String BALANCE_URL = "/internal/accounts/account-1/balance";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AuthServerStub AUTH_SERVER = new AuthServerStub();

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        AUTH_SERVER.register(registry);
    }

    @Autowired
    private AccountsPort accounts;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetDownstreams() {
        CORE_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    @Test
    @DisplayName("retries a transient 503 and returns the balance")
    void retriesATransient503AndReturnsTheBalance() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).inScenario("restart")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).inScenario("restart")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"account-1\",\"balance\":250.00,\"currency\":\"USD\"}")));

        assertEquals(new java.math.BigDecimal("250.00"), accounts.fetchBalance("account-1").balance());
        CORE_SERVICE.verify(2, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("sends a read answered 404 exactly once")
    void sendsAReadAnswered404ExactlyOnce() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).willReturn(aResponse().withStatus(404)));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> accounts.fetchBalance("account-1"));

        assertEquals(404, failure.getStatus());
        CORE_SERVICE.verify(1, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("stops after three attempts when core-service stays unavailable")
    void stopsAfterThreeAttemptsWhenCoreServiceStaysUnavailable() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).willReturn(aResponse().withStatus(503)));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> accounts.fetchBalance("account-1"));

        assertEquals(503, failure.getStatus());
        CORE_SERVICE.verify(3, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }
}
