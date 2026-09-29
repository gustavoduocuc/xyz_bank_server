package cl.duoc.xyzbank.authserver.clients.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_PASSWORD;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The channel client registrations")
class ClientRegistrationE2ETest {

    /*
     * Cases:
     * 1. An authorization request without a PKCE code challenge gets no code, for either client
     * 2. An unknown client is rejected without any redirect
     * 3. A redirect URI other than the registered one is rejected without redirecting to it
     * 4. The client_credentials and password grants issue no token
     */

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("issues no code to an authorization request without a PKCE code challenge")
    void issuesNoCodeToAnAuthorizationRequestWithoutAPkceCodeChallenge() {
        assertRedirectedWithError(
                authorizationRequest(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, null), WEB_REDIRECT_URI);
        assertRedirectedWithError(
                authorizationRequest(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI, MOBILE_SCOPES, null), MOBILE_REDIRECT_URI);
    }

    @Test
    @DisplayName("rejects an unknown client without redirecting anywhere")
    void rejectsAnUnknownClientWithoutRedirectingAnywhere() {
        Response response = flow.authorize(
                authorizationRequest("bff-atm", "https://localhost:8083/callback", "openid", newCodeVerifier()),
                DEMO_USERNAME, DEMO_PASSWORD);

        assertEquals(400, response.statusCode(), response.asString());
        assertNull(response.getHeader("Location"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://localhost:9999/login/oauth2/code/oidc",
        "https://localhost:8081/login/oauth2/code/other",
        "http://localhost:8081/login/oauth2/code/oidc",
        "https://evil.example/login/oauth2/code/oidc"
    })
    @DisplayName("rejects a redirect URI other than the registered one without redirecting to it")
    void rejectsARedirectUriOtherThanTheRegisteredOne(String redirectUri) {
        Response response = flow.authorize(
                authorizationRequest(WEB_CLIENT_ID, redirectUri, WEB_SCOPES, newCodeVerifier()),
                DEMO_USERNAME, DEMO_PASSWORD);

        assertEquals(400, response.statusCode(), response.asString());
        assertNull(response.getHeader("Location"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"client_credentials", "password"})
    @DisplayName("issues no token for grants other than authorization_code")
    void issuesNoTokenForGrantsOtherThanAuthorizationCode(String grantType) {
        Response response = flow.request()
                .auth().preemptive().basic(WEB_CLIENT_ID, WEB_CLIENT_SECRET)
                .contentType(ContentType.URLENC)
                .formParam("grant_type", grantType)
                .formParam("username", DEMO_USERNAME)
                .formParam("password", DEMO_PASSWORD)
                .formParam("scope", "web:accounts:read")
                .post("/oauth2/token");

        assertEquals(400, response.statusCode(), response.asString());
        assertTrue(
                Set.of("unauthorized_client", "unsupported_grant_type").contains(response.jsonPath().getString("error")),
                response.asString());
        assertNull(response.jsonPath().getString("access_token"));
    }

    private void assertRedirectedWithError(Map<String, String> authorizationRequest, String redirectUri) {
        Response response = flow.authorize(authorizationRequest, DEMO_USERNAME, DEMO_PASSWORD);

        String location = response.getHeader("Location");
        assertEquals(302, response.statusCode(), response.asString());
        assertTrue(location.startsWith(redirectUri), location);
        assertEquals("invalid_request", queryParam(location, "error"), location);
        assertNull(queryParam(location, "code"), location);
    }
}
