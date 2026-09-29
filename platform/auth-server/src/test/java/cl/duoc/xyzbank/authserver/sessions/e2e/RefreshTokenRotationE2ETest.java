package cl.duoc.xyzbank.authserver.sessions.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Migrated from core-service's RotateWebRefreshTokenUseCaseTest (1-3),
 * RotateMobileRefreshTokenUseCaseTest (2-3), WebRefreshTokenControllerE2ETest (1-3),
 * MobileRefreshTokenControllerE2ETest (1-3) and JpaRefreshTokenRepositoryIT (3), now against
 * auth-server's refresh_token grant. The former 409 for reuse is the OAuth invalid_grant (400).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The refresh token rotation")
class RefreshTokenRotationE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. A web login issues a refresh token (first issuance)
     * 2. A mobile login issues a refresh token (first issuance)
     * 3. A web refresh rotates the token: new tokens, and the used refresh token is rejected afterwards
     * 4. Reusing a rotated-out web refresh token is rejected and ends the login: the newer token dies too
     * 5. Retrying an identical, already-successful refresh issues nothing and ends the login
     * 6. A mobile refresh rotates the token, and reusing the old one ends the login
     * 7. bff-web cannot use a refresh token issued to bff-mobile
     */

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("issues a refresh token at the end of a web login")
    void issuesARefreshTokenAtTheEndOfAWebLogin() {
        Response login = flow.loggedInWebClient();

        assertEquals(200, login.statusCode(), login.asString());
        assertNotNull(login.jsonPath().getString("refresh_token"));
    }

    @Test
    @DisplayName("issues a refresh token at the end of a mobile login")
    void issuesARefreshTokenAtTheEndOfAMobileLogin() {
        Response login = flow.loggedInMobileClient(uniqueDevice());

        assertEquals(200, login.statusCode(), login.asString());
        assertNotNull(login.jsonPath().getString("refresh_token"));
    }

    @Test
    @DisplayName("rotates a web refresh token on use and rejects the used one afterwards")
    void rotatesAWebRefreshTokenOnUse() {
        String r1 = flow.loggedInWebClient().jsonPath().getString("refresh_token");

        Response refreshed = flow.refreshAsWebClient(r1);

        assertEquals(200, refreshed.statusCode(), refreshed.asString());
        String r2 = refreshed.jsonPath().getString("refresh_token");
        assertNotNull(refreshed.jsonPath().getString("access_token"));
        assertNotEquals(r1, r2);
        assertInvalidGrant(flow.refreshAsWebClient(r1));
    }

    @Test
    @DisplayName("ends the login when a rotated-out web refresh token is reused")
    void endsTheLoginWhenARotatedOutWebRefreshTokenIsReused() {
        String r1 = flow.loggedInWebClient().jsonPath().getString("refresh_token");
        String r2 = flow.refreshAsWebClient(r1).jsonPath().getString("refresh_token");

        Response reuse = flow.refreshAsWebClient(r1);

        assertInvalidGrant(reuse);
        assertInvalidGrant(flow.refreshAsWebClient(r2));
    }

    @Test
    @DisplayName("issues nothing for an identical retry of a successful refresh, and ends the login")
    void issuesNothingForAnIdenticalRetryOfASuccessfulRefresh() {
        String r1 = flow.loggedInWebClient().jsonPath().getString("refresh_token");
        Response first = flow.refreshAsWebClient(r1);
        String r2 = first.jsonPath().getString("refresh_token");

        Response retry = flow.refreshAsWebClient(r1);

        assertEquals(200, first.statusCode());
        assertInvalidGrant(retry);
        assertInvalidGrant(flow.refreshAsWebClient(r2));
    }

    @Test
    @DisplayName("rotates a mobile refresh token and ends the login when the old one is reused")
    void rotatesAMobileRefreshTokenAndEndsTheLoginOnReuse() {
        String device = uniqueDevice();
        String r1 = flow.loggedInMobileClient(device).jsonPath().getString("refresh_token");
        Response refreshed = flow.refreshAsMobileClient(r1, device);
        String r2 = refreshed.jsonPath().getString("refresh_token");

        Response reuse = flow.refreshAsMobileClient(r1, device);

        assertEquals(200, refreshed.statusCode(), refreshed.asString());
        assertInvalidGrant(reuse);
        assertInvalidGrant(flow.refreshAsMobileClient(r2, device));
    }

    @Test
    @DisplayName("keeps bff-web from using a refresh token issued to bff-mobile")
    void keepsBffWebFromUsingARefreshTokenIssuedToBffMobile() {
        String mobileRefreshToken = flow.loggedInMobileClient(uniqueDevice()).jsonPath().getString("refresh_token");

        assertInvalidGrant(flow.refreshAsWebClient(mobileRefreshToken));
    }

    private static void assertInvalidGrant(Response response) {
        assertEquals(400, response.statusCode(), response.asString());
        assertEquals("invalid_grant", response.jsonPath().getString("error"));
        assertNull(response.jsonPath().getString("access_token"));
    }

    private static String uniqueDevice() {
        return "device-" + UUID.randomUUID();
    }
}
