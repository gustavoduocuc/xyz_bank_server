package cl.duoc.xyzbank.interestsservice.testsupport;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Access tokens shaped exactly like the ones auth-server issues (RS256, iss, aud, azp, channel,
 * scope, sub, device_id), signed with a test key whose public half TestAuthServer publishes.
 * Replaces the fake "user-jwt" bearer and the shared HS256 secret interests-service's tests used.
 */
public final class TestAccessTokens {

    public static final String ISSUER = "https://localhost:9000";
    public static final String AUDIENCE = "interests-service";

    private static final RSAKey SIGNING_KEY = generate("core-test-key");
    private static final RSAKey FOREIGN_KEY = generate("foreign-key");

    private TestAccessTokens() {
    }

    public static String web(String customerId) {
        return token("bff-web", Channel.WEB).subject(customerId).sign();
    }

    public static String mobile(String customerId, String deviceId) {
        return token("bff-mobile", Channel.MOBILE).subject(customerId).deviceId(deviceId).sign();
    }

    public static String atm() {
        return token("bff-atm", Channel.ATM).subject("bff-atm").sign();
    }

    public static String interests() {
        return token("interests-service", Channel.INTERESTS).subject("interests-service").sign();
    }

    /**
     * A token for the given client and channel with that channel's full scope set, 15 minutes
     * of validity and the audiences auth-server gives that client; each part can be overridden.
     */
    public static Builder token(String clientId, Channel channel) {
        return new Builder(clientId, channel);
    }

    public static String jwksJson() {
        return new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
    }

    public static RSAKey publicKey() {
        return SIGNING_KEY.toPublicJWK();
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static final class Builder {

        private final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        private RSAKey key = SIGNING_KEY;

        private Builder(String clientId, Channel channel) {
            Instant now = Instant.now();
            claims.issuer(ISSUER)
                    .audience(channel == Channel.WEB || channel == Channel.INTERESTS ? List.of("core-service", AUDIENCE) : List.of("core-service"))
                    .claim("azp", clientId)
                    .claim("channel", channel.name())
                    .claim("scope", List.copyOf(channel.scopes()))
                    .subject(clientId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))));
        }

        public Builder subject(String subject) {
            claims.subject(subject);
            return this;
        }

        public Builder deviceId(String deviceId) {
            claims.claim("device_id", deviceId);
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            claims.claim("scope", List.copyOf(scopes));
            return this;
        }

        public Builder channel(String channel) {
            claims.claim("channel", channel);
            return this;
        }

        public Builder issuer(String issuer) {
            claims.issuer(issuer);
            return this;
        }

        public Builder audience(String audience) {
            claims.audience(List.of(audience));
            return this;
        }

        public Builder expiredAt(Instant expiry) {
            claims.issueTime(Date.from(expiry.minus(Duration.ofMinutes(15)))).expirationTime(Date.from(expiry));
            return this;
        }

        public Builder signedWithForeignKey() {
            key = FOREIGN_KEY;
            return this;
        }

        public String sign() {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
                jwt.sign(new RSASSASigner(key));
                return jwt.serialize();
            } catch (JOSEException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
