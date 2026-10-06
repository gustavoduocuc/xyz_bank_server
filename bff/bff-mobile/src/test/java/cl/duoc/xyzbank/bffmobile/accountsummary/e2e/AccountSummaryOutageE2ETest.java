package cl.duoc.xyzbank.bffmobile.accountsummary.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffmobile.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms",
                "resilience4j.circuitbreaker.instances.coreService.waitDurationInOpenState=500ms"
        })
@DisplayName("The account summary during a core-service outage")
class AccountSummaryOutageE2ETest {

    /*
     * Cases (bff-resilience spec, "The fallback never serves cached or partial data" and "Each
     * downstream service is protected by a circuit breaker"):
     * 1. With core-service down the summary answers the 503 ProblemDetail with no balance or
     *    transactions; the breaker opens after the threshold and then core-service is not called
     * 2. After the (test-shortened) wait, with core-service back, the breaker half-opens, the
     *    trial calls succeed, it closes and the summary is served again
     */

    private static final String DEVICE_ID = "device-1";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final MockOidcProvider AUTHORIZATION_SERVER = new MockOidcProvider();

    static {
        CORE_SERVICE.start();
        AUTHORIZATION_SERVER.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
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
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        coreService = circuitBreakers.circuitBreaker("coreService");
    }

    @AfterAll
    static void stopStubs() {
        CORE_SERVICE.stop();
        AUTHORIZATION_SERVER.stop();
    }

    @Test
    @DisplayName("answers 503 without balance or transactions and stops calling core-service once the circuit opens")
    void answers503WithoutBalanceOrTransactionsAndStopsCallingCoreServiceOnceTheCircuitOpens() {
        CORE_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        for (int request = 0; request < 10 && coreService.getState() != CircuitBreaker.State.OPEN; request++) {
            assertServiceUnavailableWithoutBankingData(summary());
        }
        assertEquals(CircuitBreaker.State.OPEN, coreService.getState());

        CORE_SERVICE.resetRequests();
        assertServiceUnavailableWithoutBankingData(summary());
        CORE_SERVICE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("half-opens, closes and serves the summary again once core-service recovers")
    void halfOpensClosesAndServesTheSummaryAgainOnceCoreServiceRecovers() {
        coreService.transitionToOpenState();
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/balance")).willReturn(json(
                "{\"accountId\":\"account-1\",\"balance\":250.00,\"currency\":\"USD\"}")));
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/accounts/account-1/transactions")).willReturn(json(
                "{\"items\":[],\"nextCursor\":null}")));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (coreService.getState() != CircuitBreaker.State.HALF_OPEN && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertEquals(CircuitBreaker.State.HALF_OPEN, coreService.getState());

        // Two core-service calls per summary; the breaker needs three successful trial calls
        summary().then().statusCode(200);
        summary().then().statusCode(200);

        assertEquals(CircuitBreaker.State.CLOSED, coreService.getState());
    }

    private static Response summary() {
        return given()
                .header("Authorization", "Bearer " + TestSessions.mobileSessionFor("customer-1", DEVICE_ID))
                .header("X-Device-Id", DEVICE_ID)
                .when()
                .get("/accounts/{accountId}/summary", "account-1");
    }

    private static void assertServiceUnavailableWithoutBankingData(Response response) {
        response.then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("balance", nullValue())
                .body("transactions", nullValue());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
