package cl.duoc.xyzbank.bffatm.balanceinquiry.e2e;

import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
@DisplayName("bff-atm's core-service circuit")
class CoreServiceCircuitE2ETest {

    /*
     * Cases (bff-resilience spec, "Each downstream service is protected by a circuit breaker"):
     * 1. With core-service down every request answers the 503 ProblemDetail (no balance), the
     *    breaker opens after the configured minimum calls, and then no request reaches core-service
     * 2. After the (test-shortened) wait, with core-service back, the trial calls succeed and the
     *    breaker closes
     * 3. Failing trial calls reopen the breaker
     * 4. 404 answers alone never open the breaker
     */

    private static final String TERMINAL_ID = "atm-terminal-001";
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

    @LocalServerPort
    private int port;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private CircuitBreaker coreService;

    @BeforeEach
    void configure() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.config = RestAssured.config()
                .sslConfig(SSLConfig.sslConfig()
                        .keyStore("tls/terminal-keystore.p12", "xyzbank-dev")
                        .and()
                        .relaxedHTTPSValidation());
        CORE_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        coreService = circuitBreakers.circuitBreaker("coreService");
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    @Test
    @DisplayName("opens after the failure threshold while core-service is down, then stops calling it")
    void opensAfterTheFailureThresholdWhileCoreServiceIsDown() {
        coreServiceIsDown();

        for (int request = 0; request < 10 && coreService.getState() != CircuitBreaker.State.OPEN; request++) {
            assertServiceUnavailableWithoutBalance(balanceInquiry());
        }
        assertEquals(CircuitBreaker.State.OPEN, coreService.getState());

        CORE_SERVICE.resetRequests();
        assertServiceUnavailableWithoutBalance(balanceInquiry());
        CORE_SERVICE.verify(0, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("half-opens and closes once core-service recovers")
    void halfOpensAndClosesOnceCoreServiceRecovers() {
        coreService.transitionToOpenState();
        coreServiceAnswers();

        awaitState(CircuitBreaker.State.HALF_OPEN);
        for (int trial = 0; trial < 3; trial++) {
            balanceInquiry().then().statusCode(200).body("balance", equalTo(250.00f));
        }

        assertEquals(CircuitBreaker.State.CLOSED, coreService.getState());
    }

    @Test
    @DisplayName("reopens when the trial calls fail")
    void reopensWhenTheTrialCallsFail() {
        coreService.transitionToOpenState();
        coreServiceIsDown();

        awaitState(CircuitBreaker.State.HALF_OPEN);
        for (int request = 0; request < 3 && coreService.getState() != CircuitBreaker.State.OPEN; request++) {
            assertServiceUnavailableWithoutBalance(balanceInquiry());
        }

        assertEquals(CircuitBreaker.State.OPEN, coreService.getState());
    }

    @Test
    @DisplayName("never opens on 404 answers alone")
    void neverOpensOn404AnswersAlone() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"detail\":\"Account not found\"}")));

        for (int request = 0; request < 25; request++) {
            balanceInquiry().then().statusCode(404);
        }

        assertEquals(CircuitBreaker.State.CLOSED, coreService.getState());
        CORE_SERVICE.verify(25, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    private Response balanceInquiry() {
        return given()
                .header("Authorization", "Bearer " + tokenAdapter.issue("customer-1", Channel.ATM, TERMINAL_ID))
                .when()
                .get("/accounts/{accountId}/balance", "account-1");
    }

    private static void assertServiceUnavailableWithoutBalance(Response response) {
        response.then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("balance", nullValue());
    }

    private static void coreServiceIsDown() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    private static void coreServiceAnswers() {
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accountId\":\"account-1\",\"balance\":250.00,\"currency\":\"USD\"}")));
    }

    private void awaitState(CircuitBreaker.State expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (coreService.getState() != expected && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertEquals(expected, coreService.getState());
    }
}
