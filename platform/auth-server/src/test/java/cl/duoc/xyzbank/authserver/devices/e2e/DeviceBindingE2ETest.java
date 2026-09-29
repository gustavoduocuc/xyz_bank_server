package cl.duoc.xyzbank.authserver.devices.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_PASSWORD;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.mobileAuthorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers what core-service's RotateMobileRefreshTokenUseCaseTest (cases 1, 4, 5) and
 * MobileRefreshTokenControllerE2ETest (cases 1, 4) checked, now against auth-server
 * (adopt-oauth2-tokens-between-services design.md Decision 6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The device binding of mobile logins")
class DeviceBindingE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. A mobile login without a device gets no authorization code
     * 2. The first login from a device registers it for the customer
     * 3. A login for a revoked device gets no authorization code
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

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
    @DisplayName("issues no code to a mobile login that names no device")
    void issuesNoCodeToAMobileLoginThatNamesNoDevice() {
        Map<String, String> withoutDevice =
                authorizationRequest(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI, MOBILE_SCOPES, newCodeVerifier());

        Response response = flow.authorize(withoutDevice, DEMO_USERNAME, DEMO_PASSWORD);

        assertNull(queryParam(response.getHeader("Location"), "code"), response.getHeader("Location"));
    }

    @Test
    @DisplayName("registers the device for the customer on its first login")
    void registersTheDeviceForTheCustomerOnItsFirstLogin() {
        String deviceId = uniqueDevice();
        String verifier = newCodeVerifier();
        String code = flow.mobileAuthorizationCodeFor(deviceId, verifier);

        Response exchange = flow.exchangeAsMobileClient(code, verifier);

        assertEquals(200, exchange.statusCode(), exchange.asString());
        String owner = jdbcTemplate.queryForObject(
                "SELECT customer_id FROM device_registrations WHERE device_id = ?", String.class, deviceId);
        assertEquals(SEED_CUSTOMER, owner);
    }

    @Test
    @DisplayName("issues no code to a login for a revoked device")
    void issuesNoCodeToALoginForARevokedDevice() {
        String deviceId = uniqueDevice();
        jdbcTemplate.update(
                "INSERT INTO device_registrations (device_id, customer_id, revoked) VALUES (?, ?, true)",
                deviceId, SEED_CUSTOMER);

        Response response =
                flow.authorize(mobileAuthorizationRequest(deviceId, newCodeVerifier()), DEMO_USERNAME, DEMO_PASSWORD);

        String location = response.getHeader("Location");
        assertNotNull(location);
        assertNull(queryParam(location, "code"), location);
    }

    private static String uniqueDevice() {
        return "device-" + UUID.randomUUID();
    }
}
