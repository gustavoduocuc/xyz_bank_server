package cl.duoc.xyzbank.authserver.tokens.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.MOBILE_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The authorization code flow")
class AuthorizationCodeFlowE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. The demo customer's web login yields RS256 ID and access tokens issued by the public
     *    issuer, identifying the seed customer on the WEB channel with exactly the web scopes
     * 2. The demo customer's mobile login (confidential client, on a device) yields MOBILE tokens with
     *    exactly the mobile scopes
     * 3. Retrying an exchange with an already-used code is rejected and issues nothing
     * 4. A code verifier that does not match the challenge is rejected
     * 5. bff-web cannot exchange a code without its secret
     * 6. bff-web cannot exchange a code with a wrong secret
     * 7. The web access token names bff-web as azp, is meant for core-service and
     *    interests-service, and lives 15 minutes
     * 8. The mobile access token is bound to the login's device
     */

    private static final String ISSUER = "https://localhost:9000";
    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("issues web tokens for the seed customer on the WEB channel")
    void issuesWebTokensForTheSeedCustomerOnTheWebChannel() throws Exception {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);

        Response tokenResponse = flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET);

        assertEquals(200, tokenResponse.statusCode(), tokenResponse.asString());
        JWKSet jwks = JWKSet.parse(flow.request().get("/oauth2/jwks").asString());
        SignedJWT accessToken = SignedJWT.parse(tokenResponse.jsonPath().getString("access_token"));
        SignedJWT idToken = SignedJWT.parse(tokenResponse.jsonPath().getString("id_token"));
        for (SignedJWT token : List.of(accessToken, idToken)) {
            assertEquals(JWSAlgorithm.RS256, token.getHeader().getAlgorithm());
            String keyId = token.getHeader().getKeyID();
            assertTrue(token.verify(new RSASSAVerifier(jwks.getKeyByKeyId(keyId).toRSAKey())), "signature");
            assertEquals(ISSUER, token.getJWTClaimsSet().getIssuer());
            assertEquals(SEED_CUSTOMER, token.getJWTClaimsSet().getSubject());
            assertEquals("WEB", token.getJWTClaimsSet().getStringClaim("channel"));
        }
        assertTrue(idToken.getJWTClaimsSet().getAudience().contains(WEB_CLIENT_ID));
        assertEquals(
                Set.of(WEB_SCOPES.split(" ")),
                Set.copyOf(accessToken.getJWTClaimsSet().getStringListClaim("scope")));
    }

    @Test
    @DisplayName("issues mobile tokens with only the mobile scopes to the mobile client")
    void issuesMobileTokensWithOnlyTheMobileScopesToTheMobileClient() throws Exception {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.mobileAuthorizationCodeFor("D1", verifier);

        Response tokenResponse = flow.exchangeAsMobileClient(code, verifier);

        assertEquals(200, tokenResponse.statusCode(), tokenResponse.asString());
        SignedJWT accessToken = SignedJWT.parse(tokenResponse.jsonPath().getString("access_token"));
        SignedJWT idToken = SignedJWT.parse(tokenResponse.jsonPath().getString("id_token"));
        assertEquals("MOBILE", accessToken.getJWTClaimsSet().getStringClaim("channel"));
        assertEquals("MOBILE", idToken.getJWTClaimsSet().getStringClaim("channel"));
        assertEquals(SEED_CUSTOMER, idToken.getJWTClaimsSet().getSubject());
        assertEquals(
                Set.of(MOBILE_SCOPES.split(" ")),
                Set.copyOf(accessToken.getJWTClaimsSet().getStringListClaim("scope")));
    }

    @Test
    @DisplayName("rejects a retried exchange of an already-used code without issuing new tokens")
    void rejectsARetriedExchangeOfAnAlreadyUsedCode() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
        assertEquals(200, flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET).statusCode());

        Response retry = flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET);

        assertInvalid(retry, 400, "invalid_grant");
    }

    @Test
    @DisplayName("rejects a code verifier that does not match the challenge")
    void rejectsACodeVerifierThatDoesNotMatchTheChallenge() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.mobileAuthorizationCodeFor("D1", verifier);

        Response response = flow.exchangeAsMobileClient(code, AuthorizationCodeFlow.newCodeVerifier());

        assertInvalid(response, 400, "invalid_grant");
    }

    @Test
    @DisplayName("refuses a bff-web exchange that does not present the client secret")
    void refusesABffWebExchangeThatDoesNotPresentTheClientSecret() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);

        Response response = flow.exchangeAsWebClient(code, verifier, null);

        assertInvalid(response, 401, "invalid_client");
    }

    @Test
    @DisplayName("refuses a bff-web exchange with a wrong client secret")
    void refusesABffWebExchangeWithAWrongClientSecret() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);

        Response response = flow.exchangeAsWebClient(code, verifier, "not-the-secret");

        assertInvalid(response, 401, "invalid_client");
    }

    private static void assertInvalid(Response response, int status, String error) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(error, response.jsonPath().getString("error"));
        assertNull(response.jsonPath().getString("access_token"));
    }

    @Test
    @DisplayName("issues a web access token for bff-web, meant for core-service and interests-service, living 15 minutes")
    void issuesAWebAccessTokenForBffWebMeantForCoreServiceAndInterestsService() throws Exception {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);

        SignedJWT accessToken = SignedJWT.parse(
                flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET).jsonPath().getString("access_token"));

        var claims = accessToken.getJWTClaimsSet();
        assertEquals("bff-web", claims.getStringClaim("azp"));
        assertEquals(Set.of("core-service", "interests-service"), Set.copyOf(claims.getAudience()));
        assertEquals(Duration.ofMinutes(15),
                Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()));
    }

    @Test
    @DisplayName("binds the mobile access token to the login's device")
    void bindsTheMobileAccessTokenToTheLoginsDevice() throws Exception {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.mobileAuthorizationCodeFor("D1", verifier);

        SignedJWT accessToken =
                SignedJWT.parse(flow.exchangeAsMobileClient(code, verifier).jsonPath().getString("access_token"));

        assertEquals("D1", accessToken.getJWTClaimsSet().getStringClaim("device_id"));
        assertEquals(List.of("core-service"), accessToken.getJWTClaimsSet().getAudience());
        assertEquals("bff-mobile", accessToken.getJWTClaimsSet().getStringClaim("azp"));
    }
}
