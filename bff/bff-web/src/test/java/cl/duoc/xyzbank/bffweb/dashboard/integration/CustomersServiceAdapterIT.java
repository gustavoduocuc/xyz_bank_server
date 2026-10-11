package cl.duoc.xyzbank.bffweb.dashboard.integration;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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

@SpringBootTest(properties = "resilience4j.retry.instances.customersServiceRead.waitDuration=10ms")
@DisplayName("bff-web's customers-service profile adapter")
class CustomersServiceAdapterIT {

    /*
     * Cases:
     * 1. Maps customers-service's profile response
     * 2. Sends a read answered 404 exactly once and surfaces the 404
     * 3. Retries a transient 503 and serves the profile
     * 4. A customers-service outage opens only its own breaker; core-service reads are still served
     */

    private static final String PROFILE_PATH = "/internal/customers/customer-1";
    private static final String PROFILE =
            "{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\",\"version\":0}";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final WireMockServer CUSTOMERS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
        CUSTOMERS_SERVICE.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        registry.add("customers-service.base-url", CUSTOMERS_SERVICE::baseUrl);
    }

    @Autowired
    private CustomerProfilePort profiles;

    @Autowired
    private AccountsPort accounts;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetDownstreams() {
        CORE_SERVICE.resetAll();
        CUSTOMERS_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        CUSTOMERS_SERVICE.stop();
    }

    @Test
    @DisplayName("maps customers-service's profile response")
    void mapsCustomersServicesProfileResponse() {
        CUSTOMERS_SERVICE.stubFor(get(urlPathEqualTo(PROFILE_PATH)).willReturn(json(PROFILE)));

        CustomerProfile profile = profiles.fetchProfile("customer-1");

        assertEquals(new CustomerProfile("customer-1", "Ana Perez", "ana@example.com"), profile);
    }

    @Test
    @DisplayName("sends a read answered 404 exactly once and surfaces the 404")
    void sendsAReadAnswered404ExactlyOnceAndSurfacesThe404() {
        CUSTOMERS_SERVICE.stubFor(get(urlPathEqualTo(PROFILE_PATH)).willReturn(aResponse().withStatus(404)));

        CoreServiceCallException failure =
                assertThrows(CoreServiceCallException.class, () -> profiles.fetchProfile("customer-1"));

        assertEquals(404, failure.getStatus());
        CUSTOMERS_SERVICE.verify(1, getRequestedFor(urlPathEqualTo(PROFILE_PATH)));
    }

    @Test
    @DisplayName("retries a transient 503 and serves the profile")
    void retriesATransient503AndServesTheProfile() {
        CUSTOMERS_SERVICE.stubFor(get(urlPathEqualTo(PROFILE_PATH)).inScenario("restart")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        CUSTOMERS_SERVICE.stubFor(get(urlPathEqualTo(PROFILE_PATH)).inScenario("restart")
                .whenScenarioStateIs("recovered")
                .willReturn(json(PROFILE)));

        assertEquals("Ana Perez", profiles.fetchProfile("customer-1").fullName());
        CUSTOMERS_SERVICE.verify(2, getRequestedFor(urlPathEqualTo(PROFILE_PATH)));
    }

    @Test
    @DisplayName("opens only the customers-service breaker and keeps serving core-service reads")
    void opensOnlyTheCustomersServiceBreakerAndKeepsServingCoreServiceReads() {
        CUSTOMERS_SERVICE.stubFor(get(urlPathEqualTo(PROFILE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/customers/customer-1/accounts")).willReturn(json("[]")));
        CircuitBreaker customersBreaker = circuitBreakers.circuitBreaker("customersService");

        for (int request = 0; request < 5 && customersBreaker.getState() != CircuitBreaker.State.OPEN; request++) {
            // Unavailable, or refused by the breaker if it opened between this request's attempts
            assertThrows(RuntimeException.class, () -> profiles.fetchProfile("customer-1"));
        }

        assertEquals(CircuitBreaker.State.OPEN, customersBreaker.getState());
        assertThrows(CallNotPermittedException.class, () -> profiles.fetchProfile("customer-1"));
        assertTrue(accounts.fetchAccountsForCustomer("customer-1").isEmpty());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.circuitBreaker("coreService").getState());
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
