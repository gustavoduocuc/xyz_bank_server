package cl.duoc.xyzbank.bffmobile.auth.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * A WireMock-backed stand-in for the OIDC provider bff-web registers against in dev/test
 * (design.md Decision 4: "mocked in tests"). Signs ID tokens with its own throwaway RSA key
 * and serves the matching public JWK set, so Spring Security's real authorization-code
 * exchange and ID-token signature verification run against it unmodified.
 */
public final class MockOidcProvider {

    private static final String CLIENT_ID = "xyz-bank-mobile-dev";
    private static final String ISSUER = "http://localhost:9999/mock-oidc";

    private final WireMockServer server;
    private final RSAKey rsaKey;

    public MockOidcProvider() {
        this.server = new WireMockServer(wireMockConfig().port(9999));
        this.rsaKey = generateKey();
    }

    public void start() {
        server.start();
        server.stubFor(get(urlPathEqualTo("/mock-oidc/jwks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString())));
    }

    public void stop() {
        server.stop();
    }

    public void resetAll() {
        server.resetAll();
        start();
    }

    /**
     * Stubs the token endpoint to exchange the given authorization code for tokens
     * identifying subject. The nonce must be exactly the "nonce" query parameter value bff-web
     * sent on the authorization redirect (a real OIDC provider echoes it back into the ID
     * token verbatim; it never sees or computes bff-web's raw, pre-hash nonce).
     */
    public void stubSuccessfulTokenExchange(String code, String subject, String nonce) {
        stubSuccessfulTokenExchange(code, subject, nonce, ISSUER);
    }

    /**
     * Same as {@link #stubSuccessfulTokenExchange(String, String, String)}, but the correctly
     * signed ID token claims the given issuer -- to prove bff-mobile rejects a foreign one.
     */
    public void stubSuccessfulTokenExchange(String code, String subject, String nonce, String issuer) {
        String idToken = signIdToken(subject, nonce, issuer);
        server.stubFor(post(urlPathEqualTo("/mock-oidc/token"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("code=" + code))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"access_token\":\"mock-access-token\","
                                + "\"token_type\":\"Bearer\","
                                + "\"expires_in\":3600,"
                                + "\"id_token\":\"" + idToken + "\"}")));
    }

    /**
     * Verifies the client exchanged its code as a public client: identified by client_id in
     * the body, with no client secret anywhere (neither Basic auth nor client_secret).
     */
    public void verifyTokenExchangeWithoutClientSecret() {
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo("/mock-oidc/token"))
                .withoutHeader("Authorization")
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("client_id=" + CLIENT_ID))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("client_secret"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("code_verifier=")));
    }

    public void stubFailedTokenExchange(String code) {
        server.stubFor(post(urlPathEqualTo("/mock-oidc/token"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("code=" + code))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    private String signIdToken(String subject, String nonce, String issuer) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(CLIENT_ID)
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .issueTime(new Date())
                    .claim("email", subject + "@customers.xyzbank.cl")
                    .claim("nonce", nonce)
                    .build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(), claims);
            signedJWT.sign(new RSASSASigner(rsaKey));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign mock ID token", exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("mock-oidc-key").generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate mock OIDC signing key", exception);
        }
    }
}
