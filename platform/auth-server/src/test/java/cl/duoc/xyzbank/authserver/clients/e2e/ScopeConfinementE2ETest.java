package cl.duoc.xyzbank.authserver.clients.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_PASSWORD;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The channel scope confinement")
class ScopeConfinementE2ETest {

    /*
     * Cases:
     * 1. bff-web cannot obtain any mobile:* scope, alone or alongside its own scopes
     * 2. bff-mobile cannot obtain any web:* scope, alone or alongside its own scopes
     * 3. Neither client can obtain ATM or interests scopes
     */

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "openid mobile:accounts:read",
        "openid mobile:transactions:read",
        "openid web:accounts:read web:customers:read web:transactions:read web:interests:read mobile:accounts:read"
    })
    @DisplayName("keeps bff-web from obtaining mobile scopes")
    void keepsBffWebFromObtainingMobileScopes(String scopes) {
        assertInvalidScope(WEB_CLIENT_ID, WEB_REDIRECT_URI, scopes);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "openid web:accounts:read",
        "openid web:interests:read",
        "openid mobile:accounts:read mobile:transactions:read web:customers:read"
    })
    @DisplayName("keeps bff-mobile from obtaining web scopes")
    void keepsBffMobileFromObtainingWebScopes(String scopes) {
        assertInvalidScope(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI, scopes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"openid atm:read-balance", "openid atm:withdraw", "openid interests:write"})
    @DisplayName("keeps both clients from obtaining ATM or interests scopes")
    void keepsBothClientsFromObtainingAtmOrInterestsScopes(String scopes) {
        assertInvalidScope(WEB_CLIENT_ID, WEB_REDIRECT_URI, scopes);
        assertInvalidScope(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI, scopes);
    }

    private void assertInvalidScope(String clientId, String redirectUri, String scopes) {
        Response response = flow.authorize(
                authorizationRequest(clientId, redirectUri, scopes, newCodeVerifier()), DEMO_USERNAME, DEMO_PASSWORD);

        String location = response.getHeader("Location");
        assertEquals(302, response.statusCode(), response.asString());
        assertTrue(location.startsWith(redirectUri), location);
        assertEquals("invalid_scope", queryParam(location, "error"), location);
        assertNull(queryParam(location, "code"), location);
    }
}
