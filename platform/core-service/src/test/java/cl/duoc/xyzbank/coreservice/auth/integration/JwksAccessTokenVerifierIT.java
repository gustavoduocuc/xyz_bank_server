package cl.duoc.xyzbank.coreservice.auth.integration;

import cl.duoc.xyzbank.coreservice.auth.application.dto.VerifiedAccessToken;
import cl.duoc.xyzbank.coreservice.auth.application.ports.InvalidAccessTokenException;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.AccessTokenDecoders;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.JwtAccessTokenVerifier;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import cl.duoc.xyzbank.testsupport.TestJwksServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The JWKS access token verifier")
class JwksAccessTokenVerifierIT {

    /*
     * Cases:
     * 1. Accepts a web token and exposes its client, channel, scopes and customer
     * 2. Keeps the device of a mobile token
     * 3. Rejects a token signed by a key the JWKS does not publish
     * 4. Rejects a token from another issuer
     * 5. Rejects a token meant for another audience
     * 6. Rejects an expired token
     * 7. Rejects a token whose client does not match its channel
     * 8. Grants only the scopes that belong to the token's channel
     */

    private static final Map<String, Channel> CHANNELS_BY_CLIENT = Map.of(
            "bff-web", Channel.WEB, "bff-mobile", Channel.MOBILE,
            "bff-atm", Channel.ATM, "interests-service", Channel.INTERESTS);

    private final JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
            AccessTokenDecoders.fromJwkSetUri(TestJwksServer.jwkSetUri(), TestAccessTokens.ISSUER, "core-service"),
            CHANNELS_BY_CLIENT);

    @Test
    @DisplayName("accepts a web token and exposes its client, channel, scopes and customer")
    void acceptsAWebTokenAndExposesItsClaims() {
        VerifiedAccessToken token = verifier.verify(TestAccessTokens.web("customer-1"));

        assertEquals(new VerifiedAccessToken(
                "bff-web", Channel.WEB, Channel.WEB.scopes(), "customer-1", Optional.empty()), token);
    }

    @Test
    @DisplayName("keeps the device of a mobile token")
    void keepsTheDeviceOfAMobileToken() {
        VerifiedAccessToken token = verifier.verify(TestAccessTokens.mobile("customer-1", "D1"));

        assertEquals(Optional.of("D1"), token.deviceId());
    }

    @Test
    @DisplayName("rejects a token signed by a key the JWKS does not publish")
    void rejectsATokenSignedByAKeyTheJwksDoesNotPublish() {
        String forged = TestAccessTokens.token("bff-web", Channel.WEB).subject("customer-1").signedWithForeignKey().sign();

        assertThrows(InvalidAccessTokenException.class, () -> verifier.verify(forged));
    }

    @Test
    @DisplayName("rejects a token from another issuer")
    void rejectsATokenFromAnotherIssuer() {
        String token = TestAccessTokens.token("bff-web", Channel.WEB).issuer("https://impostor.example").sign();

        assertThrows(InvalidAccessTokenException.class, () -> verifier.verify(token));
    }

    @Test
    @DisplayName("rejects a token meant for another audience")
    void rejectsATokenMeantForAnotherAudience() {
        String token = TestAccessTokens.token("bff-web", Channel.WEB).audience("interests-service").sign();

        assertThrows(InvalidAccessTokenException.class, () -> verifier.verify(token));
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsAnExpiredToken() {
        String token = TestAccessTokens.token("bff-web", Channel.WEB).expiredAt(Instant.parse("2020-01-01T00:00:00Z")).sign();

        assertThrows(InvalidAccessTokenException.class, () -> verifier.verify(token));
    }

    @Test
    @DisplayName("rejects a token whose client does not match its channel")
    void rejectsATokenWhoseClientDoesNotMatchItsChannel() {
        String token = TestAccessTokens.token("bff-web", Channel.WEB).channel("MOBILE").sign();

        assertThrows(InvalidAccessTokenException.class, () -> verifier.verify(token));
    }

    @Test
    @DisplayName("grants only the scopes that belong to the token's channel")
    void grantsOnlyTheScopesThatBelongToTheTokensChannel() {
        String token = TestAccessTokens.token("bff-web", Channel.WEB)
                .scopes(Set.of("web:accounts:read", "atm:withdraw", "openid"))
                .sign();

        assertEquals(Set.of("web:accounts:read"), verifier.verify(token).scopes());
    }
}
