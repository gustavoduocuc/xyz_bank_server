package cl.duoc.xyzbank.bffweb.interestview.integration;

import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "resilience4j.retry.instances.interestsServiceRead.waitDuration=10ms",
        "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms"
})
@DisplayName("bff-web's interest reads under failure")
class InterestsServiceResilienceIT {

    /*
     * Cases (bff-resilience spec):
     * 1. A transient 503 from interests-service is retried and the summary is served
     * 2. A 404 from interests-service is sent exactly once
     * 3. A persistent 503 stops after 3 attempts with the dependency unavailable
     * 4. interests-service failures open its own breaker; the core-service breaker stays closed
     *    and core-service reads (the dashboard's) are still served
     * 5. The core-service interest adapter (feature off) is retried under the core-service policy
     */

    private static final String SUMMARY_PATH = "/accounts/account-1/interest-summary";
    private static final String CORE_SUMMARY_PATH = "/internal/accounts/account-1/interest-summary";
    private static final String SUMMARY = "{\"accountId\":\"account-1\",\"year\":2025,\"openingBalance\":100.00,"
            + "\"closingBalance\":105.00,\"interestRate\":0.05,\"interestAmount\":5.00,\"currency\":\"USD\"}";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final WireMockServer INTERESTS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
        INTERESTS_SERVICE.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        registry.add("interests-service.base-url", INTERESTS_SERVICE::baseUrl);
    }

    @Autowired
    @Qualifier("interestsServiceAdapter")
    private InterestPort interestsService;

    @Autowired
    @Qualifier("httpInterestAdapter")
    private InterestPort coreServiceInterests;

    @Autowired
    private AccountsPort accounts;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetDownstreams() {
        CORE_SERVICE.resetAll();
        INTERESTS_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        INTERESTS_SERVICE.stop();
    }

    @Test
    @DisplayName("retries a transient 503 from interests-service")
    void retriesATransient503FromInterestsService() {
        stubTransientFailure(INTERESTS_SERVICE, SUMMARY_PATH);

        assertEquals("account-1", interestsService.fetchSummary("account-1", "2025").accountId());
        INTERESTS_SERVICE.verify(2, getRequestedFor(urlPathEqualTo(SUMMARY_PATH)));
    }

    @Test
    @DisplayName("sends an interests-service read answered 404 exactly once")
    void sendsAnInterestsServiceReadAnswered404ExactlyOnce() {
        INTERESTS_SERVICE.stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(aResponse().withStatus(404)));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> interestsService.fetchSummary("account-1", "2025"));

        assertEquals(404, failure.getStatus());
        INTERESTS_SERVICE.verify(1, getRequestedFor(urlPathEqualTo(SUMMARY_PATH)));
    }

    @Test
    @DisplayName("stops after three attempts when interests-service stays unavailable")
    void stopsAfterThreeAttemptsWhenInterestsServiceStaysUnavailable() {
        INTERESTS_SERVICE.stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(aResponse().withStatus(503)));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> interestsService.fetchSummary("account-1", "2025"));

        assertEquals(503, failure.getStatus());
        INTERESTS_SERVICE.verify(3, getRequestedFor(urlPathEqualTo(SUMMARY_PATH)));
    }

    @Test
    @DisplayName("opens only the interests-service breaker and keeps serving core-service reads")
    void opensOnlyTheInterestsServiceBreakerAndKeepsServingCoreServiceReads() {
        INTERESTS_SERVICE.stubFor(get(urlPathEqualTo(SUMMARY_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/customers/customer-1/accounts")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));
        CircuitBreaker interestsBreaker = circuitBreakers.circuitBreaker("interestsService");

        for (int request = 0; request < 5 && interestsBreaker.getState() != CircuitBreaker.State.OPEN; request++) {
            // Unavailable, or refused by the breaker if it opened between this request's attempts
            assertThrows(RuntimeException.class, () -> interestsService.fetchSummary("account-1", "2025"));
        }

        assertEquals(CircuitBreaker.State.OPEN, interestsBreaker.getState());
        assertThrows(CallNotPermittedException.class, () -> interestsService.fetchSummary("account-1", "2025"));
        assertTrue(accounts.fetchAccountsForCustomer("customer-1").isEmpty());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.circuitBreaker("coreService").getState());
    }

    @Test
    @DisplayName("retries the core-service interest read under the core-service policy")
    void retriesTheCoreServiceInterestReadUnderTheCoreServicePolicy() {
        stubTransientFailure(CORE_SERVICE, CORE_SUMMARY_PATH);

        assertEquals("account-1", coreServiceInterests.fetchSummary("account-1", "2025").accountId());
        CORE_SERVICE.verify(2, getRequestedFor(urlPathEqualTo(CORE_SUMMARY_PATH)));
    }

    private static void stubTransientFailure(WireMockServer server, String path) {
        server.stubFor(get(urlPathEqualTo(path)).inScenario("restart")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        server.stubFor(get(urlPathEqualTo(path)).inScenario("restart")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUMMARY)));
    }
}
