package cl.duoc.xyzbank.bffatm.shared.e2e;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "resilience4j.retry.instances.authServerToken.waitDuration=10ms")
@DisplayName("bff-atm's client token when auth-server fails")
class ClientCredentialsTokenResilienceE2ETest {

    /*
     * Cases (bff-resilience spec, "An unavailable auth-server yields 503"):
     * 1. A transient 503 from the token endpoint is retried and the balance is served
     * 2. A refused token request (401) is sent once, core-service is never called, and the
     *    terminal gets the 503 ProblemDetail
     * 3. An unreachable token endpoint is tried at most 3 times, core-service is never called,
     *    and the terminal gets the 503 ProblemDetail
     * 4. Repeated token failures open the auth-server breaker while the core-service breaker
     *    stays closed
     *
     * The stub's tokens live 60 s, inside the interceptor's 60 s refresh margin, so no token is
     * cached between requests and every request asks for one.
     */

    private static final String TERMINAL_ID = "atm-terminal-001";
    private static final String BALANCE_URL = "/internal/accounts/account-1/balance";
    private static final String TOKEN_PATH = "/oauth2/token";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final WireMockServer AUTH_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        registry.add("auth-server.token-uri", () -> AUTH_SERVER.baseUrl() + TOKEN_PATH);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

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
        AUTH_SERVER.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        CORE_SERVICE.stubFor(get(urlEqualTo(BALANCE_URL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accountId\":\"account-1\",\"balance\":250.00,\"currency\":\"USD\"}")));
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    @Test
    @DisplayName("retries a transient token failure and serves the balance")
    void retriesATransientTokenFailureAndServesTheBalance() {
        AUTH_SERVER.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("restart")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        AUTH_SERVER.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("restart")
                .whenScenarioStateIs("recovered")
                .willReturn(token()));

        balanceInquiry().then().statusCode(200).body("balance", equalTo(250.00f));

        AUTH_SERVER.verify(2, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        CORE_SERVICE.verify(1, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("sends a refused token request once and never calls core-service")
    void sendsARefusedTokenRequestOnceAndNeverCallsCoreService() {
        AUTH_SERVER.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"invalid_client\"}")));

        assertServiceUnavailableWithoutBalance(balanceInquiry());

        AUTH_SERVER.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        CORE_SERVICE.verify(0, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("tries an unreachable token endpoint at most three times and never calls core-service")
    void triesAnUnreachableTokenEndpointAtMostThreeTimes() {
        authServerIsDown();

        assertServiceUnavailableWithoutBalance(balanceInquiry());

        AUTH_SERVER.verify(3, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        CORE_SERVICE.verify(0, getRequestedFor(urlEqualTo(BALANCE_URL)));
    }

    @Test
    @DisplayName("opens the auth-server breaker without touching the core-service breaker")
    void opensTheAuthServerBreakerWithoutTouchingTheCoreServiceBreaker() {
        authServerIsDown();
        CircuitBreaker authServer = circuitBreakers.circuitBreaker("authServer");

        for (int request = 0; request < 5 && authServer.getState() != CircuitBreaker.State.OPEN; request++) {
            assertServiceUnavailableWithoutBalance(balanceInquiry());
        }

        assertEquals(CircuitBreaker.State.OPEN, authServer.getState());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.circuitBreaker("coreService").getState());
        assertEquals(0, circuitBreakers.circuitBreaker("coreService").getMetrics().getNumberOfFailedCalls());
        CORE_SERVICE.verify(0, getRequestedFor(urlEqualTo(BALANCE_URL)));
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

    private static void authServerIsDown() {
        AUTH_SERVER.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    private static ResponseDefinitionBuilder token() {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"atm-token\",\"expires_in\":60,\"token_type\":\"Bearer\"}");
    }
}
