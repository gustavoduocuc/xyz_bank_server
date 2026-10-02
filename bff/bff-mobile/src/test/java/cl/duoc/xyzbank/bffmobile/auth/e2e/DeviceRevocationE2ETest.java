package cl.duoc.xyzbank.bffmobile.auth.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-mobile's device revocation endpoint")
class DeviceRevocationE2ETest {

    /*
     * Cases:
     * 1. A successful revocation is forwarded to the authorization server, with the client
     *    secret and the device's own access token, and returns 204
     * 2. A second device belonging to the same customer is unaffected by another's revocation
     * 3. A device may not revoke a device other than itself
     * 4. With the authorization server unreachable the revocation is sent exactly once and the
     *    device gets the 503 ProblemDetail (bff-resilience spec, "An unavailable auth-server
     *    yields 503")
     */

    private static final MockOidcProvider AUTHORIZATION_SERVER = new MockOidcProvider();

    @BeforeAll
    static void startAuthorizationServer() {
        AUTHORIZATION_SERVER.start();
    }

    @AfterAll
    static void stopAuthorizationServer() {
        AUTHORIZATION_SERVER.stop();
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        AUTHORIZATION_SERVER.resetAll();
    }

    private String sessionFor(String deviceId) {
        return MockOidcProvider.mobileAccessTokenFor("customer-1", deviceId);
    }

    private RequestSpecification asDevice(String deviceId) {
        return given()
                .header("Authorization", "Bearer " + sessionFor(deviceId))
                .header("X-Device-Id", deviceId);
    }

    @Test
    @DisplayName("forwards a successful revocation to the authorization server")
    void forwardsASuccessfulRevocationToTheAuthorizationServer() {
        AUTHORIZATION_SERVER.stubDeviceRevocation("device-1");
        String deviceToken = sessionFor("device-1");

        given()
                .header("Authorization", "Bearer " + deviceToken)
                .header("X-Device-Id", "device-1")
                .when()
                .post("/devices/{deviceId}/revocations", "device-1")
                .then()
                .statusCode(204);

        List<LoggedRequest> revocations = AUTHORIZATION_SERVER.revocationRequests("device-1");
        assertEquals(1, revocations.size());
        assertTrue(revocations.getFirst().getHeader("Authorization").startsWith("Basic "));
        assertEquals(deviceToken, revocations.getFirst().queryParameter("access_token").firstValue());
    }

    @Test
    @DisplayName("leaves a second device belonging to the same customer unaffected")
    void leavesASecondDeviceBelongingToTheSameCustomerUnaffected() {
        AUTHORIZATION_SERVER.stubDeviceRevocation("device-1");
        AUTHORIZATION_SERVER.stubDeviceRevocation("device-2");

        asDevice("device-1")
                .when()
                .post("/devices/{deviceId}/revocations", "device-1")
                .then()
                .statusCode(204);

        asDevice("device-2")
                .when()
                .post("/devices/{deviceId}/revocations", "device-2")
                .then()
                .statusCode(204);

        assertEquals(1, AUTHORIZATION_SERVER.revocationRequests("device-1").size());
        assertEquals(1, AUTHORIZATION_SERVER.revocationRequests("device-2").size());
    }

    @Test
    @DisplayName("rejects a device attempting to revoke a different device")
    void rejectsADeviceAttemptingToRevokeADifferentDevice() {
        AUTHORIZATION_SERVER.stubDeviceRevocation("device-2");

        asDevice("device-1")
                .when()
                .post("/devices/{deviceId}/revocations", "device-2")
                .then()
                .statusCode(403)
                .contentType("application/problem+json");

        assertEquals(0, AUTHORIZATION_SERVER.revocationRequests("device-2").size());
    }

    @Test
    @DisplayName("sends a revocation once and answers 503 when the authorization server is unreachable")
    void sendsARevocationOnceAndAnswers503WhenTheAuthorizationServerIsUnreachable() {
        AUTHORIZATION_SERVER.stubUnreachableDeviceRevocation("device-1");

        asDevice("device-1")
                .when()
                .post("/devices/{deviceId}/revocations", "device-1")
                .then()
                .statusCode(503)
                .contentType("application/problem+json");

        assertEquals(1, AUTHORIZATION_SERVER.revocationRequests("device-1").size());
    }
}
