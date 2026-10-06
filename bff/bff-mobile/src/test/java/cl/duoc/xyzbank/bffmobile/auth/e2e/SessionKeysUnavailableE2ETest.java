package cl.duoc.xyzbank.bffmobile.auth.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffmobile.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

/*
 * Its own Spring context (the property below is unique to this class), so its session-token
 * decoder has never fetched -- and cached -- the authorization server's signing keys.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth-server.read-timeout-ms=2999")
@DisplayName("bff-mobile when the authorization server's signing keys cannot be fetched")
class SessionKeysUnavailableE2ETest {

    /*
     * Cases (bff-resilience spec, "An unavailable auth-server yields 503"):
     * 1. With no cached keys and the JWK set unreachable, a request carrying a device session token
     *    answers the 503 ProblemDetail -- not 422 as for an invalid token -- and calls nothing
     *    downstream
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
    }

    @BeforeAll
    static void startAuthorizationServer() {
        OIDC_PROVIDER.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configure() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        OIDC_PROVIDER.resetAll();
        OIDC_PROVIDER.stubUnreachableJwks();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
    }

    @Test
    @DisplayName("answers 503 instead of rejecting the session as invalid")
    void answers503InsteadOfRejectingTheSessionAsInvalid() {
        given()
                .header("Authorization", "Bearer " + TestSessions.mobileSessionFor("customer-1", "device-1"))
                .header("X-Device-Id", "device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("balance", nullValue());

        CORE_SERVICE.verify(0, anyRequestedFor(anyUrl()));
    }
}
