package cl.duoc.xyzbank.authserver.signing.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
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

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The published signing key and issuer")
class JwksAndDiscoveryE2ETest {

    /*
     * Cases:
     * 1. The JWKS exposes only public RSA parameters
     * 2. The JWKS verifies the signature of an issued token by its key id
     * 3. The discovery document advertises the public issuer whichever host name it is asked through
     * 4. A token exchanged through the internal host name still carries the public issuer
     */

    private static final String PUBLIC_ISSUER = "https://localhost:9000";
    private static final Set<String> PUBLIC_RSA_PARAMETERS = Set.of("kty", "kid", "n", "e", "use", "alg");

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("exposes only public RSA parameters")
    void exposesOnlyPublicRsaParameters() throws Exception {
        JWKSet jwks = JWKSet.parse(flow.request().get("/oauth2/jwks").asString());

        assertEquals(1, jwks.getKeys().size());
        for (JWK key : jwks.getKeys()) {
            Map<String, Object> parameters = key.toJSONObject();
            assertTrue(PUBLIC_RSA_PARAMETERS.containsAll(parameters.keySet()), parameters.keySet().toString());
            assertFalse(key.isPrivate());
        }
    }

    @Test
    @DisplayName("verifies an issued token's signature by its key id")
    void verifiesAnIssuedTokensSignatureByItsKeyId() throws Exception {
        SignedJWT idToken = SignedJWT.parse(exchangeThrough(null).jsonPath().getString("id_token"));
        JWKSet jwks = JWKSet.parse(flow.request().get("/oauth2/jwks").asString());

        JWK key = jwks.getKeyByKeyId(idToken.getHeader().getKeyID());

        assertTrue(idToken.verify(new RSASSAVerifier(key.toRSAKey())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:9000", "auth-server:9000"})
    @DisplayName("advertises the public issuer whichever host name it is asked through")
    void advertisesThePublicIssuerWhicheverHostNameItIsAskedThrough(String host) {
        Response discovery = flow.request().header("Host", host).get("/.well-known/openid-configuration");

        assertEquals(200, discovery.statusCode(), discovery.asString());
        assertEquals(PUBLIC_ISSUER, discovery.jsonPath().getString("issuer"));
    }

    @Test
    @DisplayName("stamps the public issuer on tokens exchanged through the internal host name")
    void stampsThePublicIssuerOnTokensExchangedThroughTheInternalHostName() throws Exception {
        Response tokenResponse = exchangeThrough("auth-server:9000");

        assertEquals(PUBLIC_ISSUER, SignedJWT.parse(tokenResponse.jsonPath().getString("id_token"))
                .getJWTClaimsSet().getIssuer());
        assertEquals(PUBLIC_ISSUER, SignedJWT.parse(tokenResponse.jsonPath().getString("access_token"))
                .getJWTClaimsSet().getIssuer());
    }

    private Response exchangeThrough(String host) {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
        var request = flow.request();
        if (host != null) {
            request = request.header("Host", host);
        }
        Response response = request.auth().preemptive().basic(WEB_CLIENT_ID, WEB_CLIENT_SECRET)
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", WEB_REDIRECT_URI)
                .formParam("code_verifier", verifier)
                .post("/oauth2/token");
        assertEquals(200, response.statusCode(), response.asString());
        return response;
    }
}
