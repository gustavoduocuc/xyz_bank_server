package cl.duoc.xyzbank.authserver.customers.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.util.Map;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.SESSION_COOKIE;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The customer login")
class LoginE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. An unknown username and a wrong password end on the same login error and issue no code
     * 2. The login session travels in its own cookie, never in JSESSIONID, so it cannot
     *    overwrite the BFFs' session on the same host
     */

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("fails an unknown username and a wrong password identically, issuing no code")
    void failsAnUnknownUsernameAndAWrongPasswordIdentically() {
        Map<String, String> request = authorizationRequest(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, newCodeVerifier());

        Response unknownUser = loginDuring(request, "mallory", "demo-password");
        Response wrongPassword = loginDuring(request, DEMO_USERNAME, "not-the-password");

        URI failedLogin = URI.create(unknownUser.getHeader("Location"));
        assertEquals(302, unknownUser.statusCode());
        assertEquals(unknownUser.getHeader("Location"), wrongPassword.getHeader("Location"));
        assertEquals("/login", failedLogin.getPath());
        assertEquals("error", failedLogin.getQuery());
        assertNull(queryParam(wrongPassword.getHeader("Location"), "code"));
    }

    @Test
    @DisplayName("keeps its login session in its own cookie instead of JSESSIONID")
    void keepsItsLoginSessionInItsOwnCookieInsteadOfJsessionid() {
        Response response = flow.request()
                .accept(ContentType.HTML)
                .queryParams(authorizationRequest(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, newCodeVerifier()))
                .get("/oauth2/authorize");

        assertNotNull(response.getCookie(SESSION_COOKIE));
        assertFalse(response.getCookies().containsKey("JSESSIONID"));
    }

    private Response loginDuring(Map<String, String> authorizationRequest, String username, String password) {
        Response authorize = flow.request()
                .accept(ContentType.HTML)
                .queryParams(authorizationRequest)
                .get("/oauth2/authorize");
        return flow.login(authorize.getCookie(SESSION_COOKIE), username, password);
    }
}
