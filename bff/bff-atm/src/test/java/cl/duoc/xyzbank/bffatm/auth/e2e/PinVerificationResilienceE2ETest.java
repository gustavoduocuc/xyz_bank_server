package cl.duoc.xyzbank.bffatm.auth.e2e;

import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "core-service.read-timeout-ms=500")
@DisplayName("PIN verification when core-service fails")
class PinVerificationResilienceE2ETest {

    /*
     * Cases (bff-resilience spec, "PIN verification is never retried automatically"):
     * 1. A verification that exceeds the read timeout reaches core-service exactly once, and the
     *    terminal gets the 503 ProblemDetail and no session
     * 2. A 503 from core-service is not retried either
     * 3. With the core-service breaker open the PIN is not sent at all, and the terminal gets 503
     */

    private static final String PIN_URL = "/internal/auth/atm/pin-verifications";
    private static final AuthServerStub AUTH_SERVER = new AuthServerStub();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath("tls/keystore.p12")
            .keystorePassword("xyzbank-dev")
            .keyManagerPassword("xyzbank-dev"));

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.pin-verification-base-url", () -> "https://localhost:" + CORE_SERVICE.httpsPort());
        AUTH_SERVER.register(registry);
    }

    @LocalServerPort
    private int port;

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
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    @Test
    @DisplayName("sends a timed-out verification exactly once and issues no session")
    void sendsATimedOutVerificationExactlyOnceAndIssuesNoSession() {
        CORE_SERVICE.stubFor(post(urlEqualTo(PIN_URL)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"customerId\":\"customer-1\",\"atmSessionId\":\"session-1\"}")
                .withFixedDelay(1_500)));

        assertServiceUnavailableWithoutSession(submitPin());
        CORE_SERVICE.verify(1, postRequestedFor(urlEqualTo(PIN_URL)));
    }

    @Test
    @DisplayName("does not retry a verification core-service answered 503")
    void doesNotRetryAVerificationCoreServiceAnswered503() {
        CORE_SERVICE.stubFor(post(urlEqualTo(PIN_URL)).willReturn(aResponse().withStatus(503)));

        assertServiceUnavailableWithoutSession(submitPin());
        CORE_SERVICE.verify(1, postRequestedFor(urlEqualTo(PIN_URL)));
    }

    @Test
    @DisplayName("does not send the PIN while the core-service circuit is open")
    void doesNotSendThePinWhileTheCoreServiceCircuitIsOpen() {
        circuitBreakers.circuitBreaker("coreService").transitionToOpenState();

        assertServiceUnavailableWithoutSession(submitPin());
        CORE_SERVICE.verify(0, postRequestedFor(urlEqualTo(PIN_URL)));
    }

    private static Response submitPin() {
        return given()
                .contentType("application/json")
                .body(Map.of("cardNumber", "4000123412341234", "pin", "1234"))
                .when()
                .post("/pin-verifications");
    }

    private static void assertServiceUnavailableWithoutSession(Response response) {
        response.then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("sessionToken", nullValue());
    }
}
