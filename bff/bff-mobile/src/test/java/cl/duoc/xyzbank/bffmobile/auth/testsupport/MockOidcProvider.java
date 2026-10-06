package cl.duoc.xyzbank.bffmobile.auth.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * A WireMock-backed stand-in for the platform authorization server bff-mobile registers against
 * in dev/test. Signs ID tokens and JWT access tokens (including {@code device_id}) with its own
 * throwaway RSA key, serves the matching public JWK set, answers the refresh_token grant, and
 * stubs device revocation.
 *
 * <p>The key is generated once per JVM so a cached Spring context and every instance share it.
 */
public final class MockOidcProvider {

    public static final String CLIENT_ID = "xyz-bank-mobile-dev";
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

    public void stubSuccessfulTokenExchange(String code, String subject, String nonce) {
        stubSuccessfulTokenExchange(code, subject, nonce, null);
    }

    /**
     * Stubs the token endpoint to exchange the given authorization code for tokens identifying
     * subject and bound to deviceId. The nonce must be exactly the value bff-mobile sent on the
     * authorization redirect.
     */
    public void stubSuccessfulTokenExchange(String code, String subject, String nonce, String deviceId) {
        stubTokenExchange(code, signIdToken(subject, nonce, ISSUER), mobileAccessTokenFor(subject, deviceId));
    }

    public static String refreshTokenFor(String code) {
        return "refresh-for-" + code;
    }

    public void stubTokenExchangeFromForeignIssuer(String code, String subject, String nonce) {
        stubTokenExchange(code, signIdToken(subject, nonce, "https://impostor.example"),
                mobileAccessTokenFor(subject, null));
    }

    /**
     * Verifies the client exchanged its code as a public client: identified by client_id in
     * the body, with no client secret anywhere (neither Basic auth nor client_secret).
     */
    public void verifyTokenExchangeWithoutClientSecret() {
        server.verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withoutHeader("Authorization")
                .withRequestBody(containing("client_id=" + CLIENT_ID))
                .withRequestBody(notContaining("client_secret"))
                .withRequestBody(containing("code_verifier=")));
    }

    public void stubFailedTokenExchange(String code) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("code=" + code))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    public void stubRefreshGrant(String refreshToken, String deviceId, String subject, String newRefreshToken) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .withRequestBody(containing("device_id=" + deviceId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(mobileAccessTokenFor(subject, deviceId), newRefreshToken, null))));
    }

    public void stubRejectedRefreshGrant(String refreshToken, String deviceId) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .withRequestBody(containing("device_id=" + deviceId))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    /**
     * Like {@link #stubRefreshGrant}, but the answer arrives only after delay: the authorization
     * server completes the rotation while bff-mobile may already have given up waiting.
     */
    public void stubSlowRefreshGrant(
            String refreshToken, String deviceId, String subject, String newRefreshToken, Duration delay) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=" + refreshToken))
                .withRequestBody(containing("device_id=" + deviceId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse(mobileAccessTokenFor(subject, deviceId), newRefreshToken, null))
                        .withFixedDelay((int) delay.toMillis())));
    }

    /** Every token endpoint call has its connection reset, as when the authorization server is down. */
    public void stubUnreachableTokenEndpoint() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /** Revoking deviceId has its connection reset, as when the authorization server is down. */
    public void stubUnreachableDeviceRevocation(String deviceId) {
        server.stubFor(post(urlPathEqualTo("/devices/" + deviceId + "/revocations"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /** The JWK set endpoint has its connection reset, so no signing key can be fetched. */
    public void stubUnreachableJwks() {
        server.stubFor(get(urlPathEqualTo("/mock-oidc/jwks"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /** Stubs auth-server's device revocation for deviceId to succeed. */
    public void stubDeviceRevocation(String deviceId) {
        server.stubFor(post(urlPathEqualTo("/devices/" + deviceId + "/revocations"))
                .willReturn(aResponse().withStatus(204)));
    }

    public List<LoggedRequest> tokenRequests() {
        return server.findAll(postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    public List<LoggedRequest> revocationRequests(String deviceId) {
        return server.findAll(postRequestedFor(urlPathEqualTo("/devices/" + deviceId + "/revocations")));
    }

    public static String mobileAccessTokenFor(String subject, String deviceId) {
        return accessToken(subject, CLIENT_ID, "MOBILE", deviceId,
                List.of("mobile:accounts:read", "mobile:transactions:read"),
                Instant.now().plusSeconds(900), RSA_KEY);
    }

    public static String accessTokenIssuedTo(
            String clientId, String channel, String subject, List<String> scopes) {
        return accessToken(subject, clientId, channel, null, scopes, Instant.now().plusSeconds(900), RSA_KEY);
    }

    public static String expiredMobileAccessTokenFor(String subject, String deviceId, Instant expiredAt) {
        return accessToken(subject, CLIENT_ID, "MOBILE", deviceId,
                List.of("mobile:accounts:read"), expiredAt, RSA_KEY);
    }

    public static String foreignSignedMobileAccessTokenFor(String subject, String deviceId) {
        return accessToken(subject, CLIENT_ID, "MOBILE", deviceId,
                List.of("mobile:accounts:read"), Instant.now().plusSeconds(900), generateKey());
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
            String subject, String clientId, String channel, String deviceId, List<String> scopes,
            Instant expiresAt, RSAKey key) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .audience("core-service")
                    .expirationTime(Date.from(expiresAt))
                    .issueTime(Date.from(expiresAt.minusSeconds(900)))
                    .claim("azp", clientId)
                    .claim("channel", channel)
                    .claim("scope", new ArrayList<>(scopes));
            if (deviceId != null) {
                claims.claim("device_id", deviceId);
            }
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
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
