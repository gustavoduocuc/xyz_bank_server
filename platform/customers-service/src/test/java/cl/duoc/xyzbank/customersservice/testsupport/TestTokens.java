package cl.duoc.xyzbank.customersservice.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Signs RS256 tokens shaped like auth-server's and publishes the public key as a JWKS on a
 * local port, so the service verifies them through its real resource-server configuration.
 */
public final class TestTokens {

    public static final String ISSUER = "https://localhost:9000";

    private static final RSAKey KEY = generateKey();
    private static final String JWK_SET_URI = startJwksServer();

    private TestTokens() {
    }

    public static String jwkSetUri() {
        return JWK_SET_URI;
    }

    public static String web(String customerId) {
        return sign(customerId, List.of("web:accounts:read", "web:customers:read",
                "web:transactions:read", "web:interests:read"));
    }

    public static String customersAdmin() {
        return sign("customers-admin", List.of("customers:read", "customers:write"));
    }

    private static String sign(String subject, List<String> scopes) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of("customers-service"))
                .subject(subject)
                .claim("scope", scopes)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("customers-test-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String startJwksServer() {
        byte[] jwks = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/oauth2/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/jwks";
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
