package cl.duoc.xyzbank.bffweb.auth.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-web's session refresh endpoint")
class SessionRefreshE2ETest {

    /*
     * Cases:
     * 1. A successful rotation asks the authorization server for a refresh_token grant and sets a
     *    new session cookie (that access token) and a new refresh_token cookie, both
     *    HttpOnly/Secure/SameSite, plus a non-HttpOnly CSRF cookie
     * 2. A rejected or reused refresh token (invalid_grant) clears both cookies and answers 401
     * 3. No refresh_token cookie at all is rejected without calling the authorization server
     * 4. A missing or mismatched CSRF token is rejected before the authorization server is called
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
    @DisplayName("rotates both cookies through the authorization server's refresh_token grant")
    void rotatesBothCookiesThroughTheAuthorizationServersRefreshTokenGrant() throws Exception {
        OIDC_PROVIDER.stubRefreshGrant("old-refresh-token", SUBJECT, "new-refresh-token");

        Response response = given()
                .cookie("refresh_token", "old-refresh-token")
                .cookie("XSRF-TOKEN", "csrf-token-1")
                .header("X-XSRF-TOKEN", "csrf-token-1")
                .when()
                .post("/session/refresh");

        response.then().statusCode(204);
        List<String> setCookieHeaders = response.getHeaders().getValues("Set-Cookie");
        String sessionCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith("session="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session cookie set"));
        String refreshCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no refresh_token cookie set"));

        assertTrue(sessionCookie.contains("HttpOnly") && sessionCookie.contains("Secure")
                && sessionCookie.contains("SameSite"));
        assertTrue(refreshCookie.contains("new-refresh-token"));
        assertTrue(refreshCookie.contains("HttpOnly") && refreshCookie.contains("Secure")
                && refreshCookie.contains("SameSite"));
        String sessionToken = sessionCookie.substring("session=".length(), sessionCookie.indexOf(';'));
        assertEquals(SUBJECT, SignedJWT.parse(sessionToken).getJWTClaimsSet().getSubject());
        assertEquals(MockOidcProvider.CLIENT_ID, SignedJWT.parse(sessionToken).getJWTClaimsSet().getStringClaim("azp"));
        assertEquals(Channel.WEB.scopes(),
                Set.copyOf(SignedJWT.parse(sessionToken).getJWTClaimsSet().getStringListClaim("scope")));

        String csrfCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie set"));
        assertTrue(!csrfCookie.contains("HttpOnly"), "the CSRF cookie must not be HttpOnly: " + csrfCookie);

        List<LoggedRequest> tokenRequests = OIDC_PROVIDER.tokenRequests();
        assertEquals(1, tokenRequests.size());
        String body = tokenRequests.getFirst().getBodyAsString();
        assertTrue(body.contains("grant_type=refresh_token"));
        assertTrue(body.contains("refresh_token=old-refresh-token"));
        assertTrue(tokenRequests.getFirst().getHeader("Authorization").startsWith("Basic "));
    }

    @Test
    @DisplayName("clears both cookies and answers 401 when the authorization server rejects the refresh token")
    void clearsCookiesWhenTheAuthorizationServerRejectsTheRefreshToken() {
        OIDC_PROVIDER.stubRejectedRefreshGrant("reused-token");

        Response response = given()
                .cookie("refresh_token", "reused-token")
                .cookie("XSRF-TOKEN", "csrf-token-2")
                .header("X-XSRF-TOKEN", "csrf-token-2")
                .when()
                .post("/session/refresh");

        response.then().statusCode(401);
        List<String> setCookieHeaders = response.getHeaders().getValues("Set-Cookie");
        assertTrue(setCookieHeaders.stream().anyMatch(header -> header.startsWith("session=") && header.contains("Max-Age=0")));
        assertTrue(setCookieHeaders.stream()
                .anyMatch(header -> header.startsWith("refresh_token=") && header.contains("Max-Age=0")));
    }

    @Test
    @DisplayName("rejects a request with no refresh_token cookie without calling the authorization server")
    void rejectsARequestWithNoRefreshTokenCookie() {
        given()
                .cookie("XSRF-TOKEN", "csrf-token-3")
                .header("X-XSRF-TOKEN", "csrf-token-3")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(401);

        assertTrue(OIDC_PROVIDER.tokenRequests().isEmpty());
    }

    @Test
    @DisplayName("rejects a request with a missing or mismatched CSRF token before calling the authorization server")
    void rejectsARequestWithAMissingOrMismatchedCsrfToken() {
        given()
                .cookie("refresh_token", "old-refresh-token")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(403);

        given()
                .cookie("refresh_token", "old-refresh-token")
                .cookie("XSRF-TOKEN", "csrf-token-a")
                .header("X-XSRF-TOKEN", "csrf-token-b")
                .when()
                .post("/session/refresh")
                .then()
                .statusCode(403);

        assertTrue(OIDC_PROVIDER.tokenRequests().isEmpty());
    }
}
