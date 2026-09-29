package cl.duoc.xyzbank.bffweb.auth.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * A WireMock-backed stand-in for the platform authorization server bff-web registers against
 * in dev/test (design.md Decision 4: "mocked in tests"). Signs ID tokens and JWT access tokens
 * with its own throwaway RSA key and serves the matching public JWK set, so Spring Security's
 * real authorization-code exchange, ID-token verification and bff-web's access-token
 * validation all run against it unmodified. It also answers the refresh_token grant.
 *
 * <p>The key is generated once per JVM: a cached Spring context keeps the JWK set it fetched,
 * and every instance publishes the same key id.
 */
public final class MockOidcProvider {

    public static final String CLIENT_ID = "xyz-bank-web-dev";
    public static final String ISSUER = "http://localhost:9999/mock-oidc";
    private static final String TOKEN_PATH = "/mock-oidc/token";
    private static final RSAKey RSA_KEY = generateKey();

    private final WireMockServer server;
    private final RSAKey rsaKey = RSA_KEY;

    public MockOidcProvider() {
        this.server = new WireMockServer(wireMockConfig().port(9999));
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
        stubTokenExchange(code, signIdToken(subject, nonce, ISSUER), webAccessTokenFor(subject));
    }

    /** The refresh token the stubbed exchange for this authorization code hands out. */
    public static String refreshTokenFor(String code) {
        return "refresh-for-" + code;
    }

    /**
     * Same as {@link #stubSuccessfulTokenExchange(String, String, String)}, but the correctly
     * signed ID token claims a foreign issuer -- to prove bff-web rejects it.
     */
    public void stubTokenExchangeFromForeignIssuer(String code, String subject, String nonce) {
        stubTokenExchange(code, signIdToken(subject, nonce, "https://impostor.example"), webAccessTokenFor(subject));
    }

    public void stubFailedTokenExchange(String code) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("code=" + code))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    /**
     * Stubs the refresh_token grant: presenting refreshToken rotates it into newRefreshToken
     * and returns a fresh web access token for subject.
     */
    public void stubRefreshGrant(String refreshToken, String subject, String newRefreshToken) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(webAccessTokenFor(subject), newRefreshToken, null))));
    }

    /** Stubs the refresh_token grant to refuse refreshToken, as auth-server does for a reused or revoked one. */
    public void stubRejectedRefreshGrant(String refreshToken) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    /** Every call bff-web made to the token endpoint since the last reset. */
    public List<LoggedRequest> tokenRequests() {
        return server.findAll(postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    /** A valid access token auth-server would issue to bff-web at login for subject. */
    public static String webAccessTokenFor(String subject) {
        return accessToken(subject, CLIENT_ID, "WEB", List.of(
                "web:accounts:read", "web:customers:read", "web:transactions:read", "web:interests:read"),
                Instant.now().plusSeconds(900), RSA_KEY);
    }

    /** An otherwise valid access token issued to another client for another channel (e.g. bff-mobile / MOBILE). */
    public static String accessTokenIssuedTo(String clientId, String channel, String subject, List<String> scopes) {
        return accessToken(subject, clientId, channel, scopes, Instant.now().plusSeconds(900), RSA_KEY);
    }

    /** A web access token for subject that expired at expiredAt. */
    public static String expiredWebAccessTokenFor(String subject, Instant expiredAt) {
        return accessToken(subject, CLIENT_ID, "WEB", List.of("web:accounts:read"), expiredAt, RSA_KEY);
    }

    /** A web access token for subject signed by a key the provider does not publish. */
    public static String foreignSignedWebAccessTokenFor(String subject) {
        return accessToken(subject, CLIENT_ID, "WEB", List.of("web:accounts:read"),
                Instant.now().plusSeconds(900), generateKey());
    }

    private void stubTokenExchange(String code, String idToken, String accessToken) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("code=" + code))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(accessToken, refreshTokenFor(code), idToken))));
    }

    private static String tokenResponse(String accessToken, String refreshToken, String idToken) {
        return "{"
                + "\"access_token\":\"" + accessToken + "\","
                + "\"refresh_token\":\"" + refreshToken + "\","
                + "\"token_type\":\"Bearer\","
                + "\"expires_in\":899"
                + (idToken == null ? "" : ",\"id_token\":\"" + idToken + "\"")
                + "}";
    }

    private static String accessToken(
            String subject, String clientId, String channel, List<String> scopes, Instant expiresAt, RSAKey key) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .audience(List.of("core-service", "interests-service"))
                    .expirationTime(Date.from(expiresAt))
                    .issueTime(Date.from(expiresAt.minusSeconds(900)))
                    .claim("azp", clientId)
                    .claim("channel", channel)
                    .claim("scope", new ArrayList<>(scopes))
                    .build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            signedJWT.sign(new RSASSASigner(key));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign mock access token", exception);
        }
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
