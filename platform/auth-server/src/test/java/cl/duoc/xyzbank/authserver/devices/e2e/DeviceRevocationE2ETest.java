package cl.duoc.xyzbank.authserver.devices.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_CLIENT_SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrated from core-service's DeviceRevocationControllerE2ETest (1: 204 and device revoked;
 * 2: unknown device 404) and RevokeDeviceUseCaseTest case 2 (other devices unaffected).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The device revocation endpoint")
class DeviceRevocationE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. bff-mobile revokes a device with its secret and that device's token: 204, the device's
     *    refresh token stops working, another device of the same customer keeps working
     * 2. Revoking a device that is not registered reports 404
     * 3. Without client authentication, or authenticated as another client, nothing is revoked
     * 4. A token issued for another device cannot revoke this one
     * 5. Retrying a revocation succeeds again
     */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("revokes a device and leaves the customer's other devices working")
    void revokesADeviceAndLeavesTheCustomersOtherDevicesWorking() {
        String d1 = uniqueDevice();
        String d2 = uniqueDevice();
        Response loginD1 = flow.loggedInMobileClient(d1);
        Response loginD2 = flow.loggedInMobileClient(d2);

        Response revocation = revoke(d1, MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET, loginD1.jsonPath().getString("access_token"));

        assertEquals(204, revocation.statusCode(), revocation.asString());
        assertTrue(isRevoked(d1));
        assertEquals(400, flow.refreshAsMobileClient(loginD1.jsonPath().getString("refresh_token"), d1).statusCode());
        assertEquals(200, flow.refreshAsMobileClient(loginD2.jsonPath().getString("refresh_token"), d2).statusCode());
    }

    @Test
    @DisplayName("reports a device that is not registered as not found")
    void reportsADeviceThatIsNotRegisteredAsNotFound() {
        String device = uniqueDevice();
        String accessToken = flow.loggedInMobileClient(device).jsonPath().getString("access_token");
        jdbcTemplate.update("DELETE FROM device_registrations WHERE device_id = ?", device);

        Response revocation = revoke(device, MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET, accessToken);

        assertEquals(404, revocation.statusCode(), revocation.asString());
    }

    @ParameterizedTest
    @CsvSource({"'', ''", "bff-mobile, not-the-secret", "bff-web, bff-web-dev-secret", "bff-atm, bff-atm-dev-secret"})
    @DisplayName("revokes nothing without bff-mobile's client authentication")
    void revokesNothingWithoutBffMobilesClientAuthentication(String clientId, String secret) {
        String device = uniqueDevice();
        String accessToken = flow.loggedInMobileClient(device).jsonPath().getString("access_token");

        Response revocation = revoke(device, clientId, secret, accessToken);

        assertEquals(401, revocation.statusCode(), revocation.asString());
        assertFalse(isRevoked(device));
    }

    @Test
    @DisplayName("keeps a token issued for another device from revoking this one")
    void keepsATokenIssuedForAnotherDeviceFromRevokingThisOne() {
        String target = uniqueDevice();
        flow.loggedInMobileClient(target);
        String otherDevicesToken = flow.loggedInMobileClient(uniqueDevice()).jsonPath().getString("access_token");

        Response revocation = revoke(target, MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET, otherDevicesToken);

        assertEquals(403, revocation.statusCode(), revocation.asString());
        assertFalse(isRevoked(target));
    }

    @Test
    @DisplayName("succeeds again when a revocation is retried")
    void succeedsAgainWhenARevocationIsRetried() {
        String device = uniqueDevice();
        String accessToken = flow.loggedInMobileClient(device).jsonPath().getString("access_token");
        revoke(device, MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET, accessToken);

        Response retry = revoke(device, MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET, accessToken);

        assertEquals(204, retry.statusCode(), retry.asString());
        assertTrue(isRevoked(device));
    }

    private Response revoke(String deviceId, String clientId, String secret, String accessToken) {
        var request = flow.request().accept(ContentType.JSON).contentType(ContentType.URLENC)
                .formParam("access_token", accessToken);
        if (!clientId.isEmpty()) {
            request = request.auth().preemptive().basic(clientId, secret);
        }
        return request.post("/devices/{deviceId}/revocations", deviceId);
    }

    private boolean isRevoked(String deviceId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT revoked FROM device_registrations WHERE device_id = ?", Boolean.class, deviceId));
    }

    private static String uniqueDevice() {
        return "device-" + UUID.randomUUID();
    }
}
