package cl.duoc.xyzbank.bffweb.dashboard.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffweb.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms",
                "resilience4j.circuitbreaker.instances.coreService.waitDurationInOpenState=500ms"
        })
@DisplayName("The dashboard during a core-service outage")
class DashboardOutageE2ETest {

    /*
     * Cases (bff-resilience spec, "The fallback never serves cached or partial data" and "Each
     * downstream service is protected by a circuit breaker"):
     * 1. With core-service down the dashboard answers the 503 ProblemDetail with no profile,
     *    account or transaction data; the breaker opens after the threshold and then core-service
     *    is no longer called
     * 2. After the (test-shortened) wait, with core-service back, the breaker half-opens, the
     *    trial calls succeed, it closes and the dashboard is served again
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    // customers-service stays up: this test is about core-service's outage and breaker
    private static final WireMockServer CUSTOMERS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
        CUSTOMERS_SERVICE.start();
    }

    @BeforeAll
    static void startAuthorizationServer() {
        OIDC_PROVIDER.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        registry.add("customers-service.base-url", CUSTOMERS_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private CircuitBreaker coreService;

    @BeforeEach
    void configure() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        CORE_SERVICE.resetAll();
        CUSTOMERS_SERVICE.resetAll();
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1")).willReturn(json(
                "{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        coreService = circuitBreakers.circuitBreaker("coreService");
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
        CUSTOMERS_SERVICE.stop();
    }

    @Test
    @DisplayName("answers 503 without any banking data and stops calling core-service once the circuit opens")
    void answers503WithoutAnyBankingDataAndStopsCallingCoreServiceOnceTheCircuitOpens() {
        CORE_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int request = 0; request < 10 && coreService.getState() != CircuitBreaker.State.OPEN; request++) {
            assertServiceUnavailableWithoutBankingData(dashboard());
        }
        assertEquals(CircuitBreaker.State.OPEN, coreService.getState());

        CORE_SERVICE.resetRequests();
        assertServiceUnavailableWithoutBankingData(dashboard());
        CORE_SERVICE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("half-opens, closes and serves the dashboard again once core-service recovers")
    void halfOpensClosesAndServesTheDashboardAgainOnceCoreServiceRecovers() {
        coreService.transitionToOpenState();
        coreServiceAnswers();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (coreService.getState() != CircuitBreaker.State.HALF_OPEN && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertEquals(CircuitBreaker.State.HALF_OPEN, coreService.getState());

        // Each dashboard makes two core-service calls (accounts, transactions); the half-open
        // breaker needs three successful trial calls to close
        dashboard().then().statusCode(200).body("profile.fullName", equalTo("Ana Perez"));
        dashboard().then().statusCode(200).body("profile.fullName", equalTo("Ana Perez"));

        assertEquals(CircuitBreaker.State.CLOSED, coreService.getState());
    }

    private static Response dashboard() {
        return given()
                .cookie("session", TestSessions.webSessionFor("customer-1"))
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1");
    }

    private static void assertServiceUnavailableWithoutBankingData(Response response) {
        response.then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("profile", nullValue())
                .body("accounts", nullValue());
    }

    private static void coreServiceAnswers() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1/accounts")).willReturn(json(
                "[{\"id\":\"account-1\",\"accountNumber\":\"1000000001\",\"balance\":500.00,\"currency\":\"USD\"}]")));
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/accounts/account-1/transactions")).willReturn(json(
                "{\"items\":[],\"nextCursor\":null}")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
