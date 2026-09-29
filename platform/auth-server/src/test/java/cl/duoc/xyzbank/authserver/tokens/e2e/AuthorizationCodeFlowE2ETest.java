package cl.duoc.xyzbank.authserver.tokens.e2e;

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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The authorization code flow")
class AuthorizationCodeFlowE2ETest {

    /*
     * Cases:
     * 1. The demo customer's web login yields RS256 ID and access tokens issued by the public
     *    issuer, identifying the seed customer on the WEB channel with exactly the web scopes
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
}
