package cl.duoc.xyzbank.authserver.signing.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The signing key across restarts")
class SigningKeyRestartE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. A token issued before a restart still verifies, under the same key id, against the
     *    JWKS published after restarting with the same keystore
     */

    @Test
    @DisplayName("keeps tokens issued before a restart verifiable under the same key id")
    void keepsTokensIssuedBeforeARestartVerifiableUnderTheSameKeyId() throws Exception {
        SignedJWT tokenIssuedBeforeRestart;
        try (ConfigurableApplicationContext firstRun = startAuthServer()) {
            AuthorizationCodeFlow flow = new AuthorizationCodeFlow(portOf(firstRun));
            String verifier = AuthorizationCodeFlow.newCodeVerifier();
            String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
            tokenIssuedBeforeRestart = SignedJWT.parse(
                    flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET).jsonPath().getString("access_token"));
        }

        try (ConfigurableApplicationContext secondRun = startAuthServer()) {
            JWKSet jwks = JWKSet.parse(
                    new AuthorizationCodeFlow(portOf(secondRun)).request().get("/oauth2/jwks").asString());
            JWK key = jwks.getKeyByKeyId(tokenIssuedBeforeRestart.getHeader().getKeyID());

            assertNotNull(key, "the restarted server no longer publishes the token's key id");
            assertEquals(1, jwks.getKeys().size());
            assertTrue(tokenIssuedBeforeRestart.verify(new RSASSAVerifier(key.toRSAKey())));
        }
    }
}
