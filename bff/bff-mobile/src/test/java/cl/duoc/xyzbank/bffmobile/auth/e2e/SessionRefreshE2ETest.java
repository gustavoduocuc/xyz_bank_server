package cl.duoc.xyzbank.bffmobile.auth.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-mobile's session refresh endpoint")
class SessionRefreshE2ETest {

    /*
     * Cases:
     * 1. {deviceId, refreshToken} rotates through the authorization server's refresh_token grant,
     *    naming that device_id, and returns the same body shape as login
     * 2. A device-id mismatch is rejected with 401
     * 3. Reuse of a rotated refresh token is rejected with 401
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final String SUBJECT = "customer-42";

    @BeforeAll
    static void startAuthorizationServer() {
        OIDC_PROVIDER.start();
    }

    @AfterAll
    static void stopAuthorizationServer() {
        OIDC_PROVIDER.stop();
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        OIDC_PROVIDER.resetAll();
    }

    @Test
    @DisplayName("rotates the session through the authorization server for the same device")
    void rotatesTheSessionThroughTheAuthorizationServerForTheSameDevice() throws Exception {
        OIDC_PROVIDER.stubRefreshGrant("old-refresh-token", "device-1", SUBJECT, "new-refresh-token");

        String sessionToken = given()
                .contentType("application/json")
                .body("{\"deviceId\":\"device-1\",\"refreshToken\":\"old-refresh-token\"}")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(200)
                .body("refreshToken", equalTo("new-refresh-token"))
                .body("refreshTokenExpiry", notNullValue())
                .extract()
                .path("sessionToken");

        assertEquals(SUBJECT, SignedJWT.parse(sessionToken).getJWTClaimsSet().getSubject());
        assertEquals("device-1", SignedJWT.parse(sessionToken).getJWTClaimsSet().getStringClaim("device_id"));

        List<LoggedRequest> tokenRequests = OIDC_PROVIDER.tokenRequests();
        assertEquals(1, tokenRequests.size());
        String body = tokenRequests.getFirst().getBodyAsString();
        assertTrue(body.contains("grant_type=refresh_token"));
        assertTrue(body.contains("refresh_token=old-refresh-token"));
        assertTrue(body.contains("device_id=device-1"));
        assertTrue(tokenRequests.getFirst().getHeader("Authorization").startsWith("Basic "));
    }

    @Test
    @DisplayName("rejects a refresh presented for a different device")
    void rejectsARefreshPresentedForADifferentDevice() {
        OIDC_PROVIDER.stubRejectedRefreshGrant("wrong-device-token", "device-2");

        given()
                .contentType("application/json")
                .body("{\"deviceId\":\"device-2\",\"refreshToken\":\"wrong-device-token\"}")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("rejects reuse of an already-rotated refresh token")
    void rejectsReuseOfAnAlreadyRotatedRefreshToken() {
        OIDC_PROVIDER.stubRejectedRefreshGrant("reused-token", "device-1");

        given()
                .contentType("application/json")
                .body("{\"deviceId\":\"device-1\",\"refreshToken\":\"reused-token\"}")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(401);
    }
}
