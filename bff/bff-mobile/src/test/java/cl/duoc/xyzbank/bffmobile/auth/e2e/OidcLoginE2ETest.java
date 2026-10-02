package cl.duoc.xyzbank.bffmobile.auth.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-mobile's OIDC login")
class OidcLoginE2ETest {

    /*
     * Cases:
     * 1. A successful OIDC callback with a supplied device identifier returns the authorization
     *    server's access token (bound to that device) and its refresh token in the response
     *    body, and calls no core-service endpoint
     * 2. A callback where the provider denied authentication returns no tokens and never
     *    calls core-service
     * 3. The authorization redirect asks for PKCE (S256), device_id, and exactly openid,
     *    profile and the mobile channel's scope set
     * 4. The code is exchanged as the confidential client: PKCE verifier and client secret
     * 5. A correctly signed ID token from an unexpected issuer fails the login: no tokens in the
     *    body, and core-service is never called
     * 6. Replaying an already-processed callback returns no second session, exchanging the
     *    authorization code only once
     * 7. A callback while the authorization server's token endpoint is unreachable answers the
     *    503 ProblemDetail with no tokens, and sends the (single-use) code exchange only once
     *    (bff-resilience spec, "An unavailable auth-server yields 503")
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startServers() {
        OIDC_PROVIDER.start();
        CORE_SERVICE.start();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        OIDC_PROVIDER.resetAll();
        CORE_SERVICE.resetAll();
    }

    @Test
    @DisplayName("returns the authorization server's device-bound tokens and calls no core-service endpoint")
    void returnsTheAuthorizationServersDeviceBoundTokensAndCallsNoCoreServiceEndpoint() throws Exception {
        String code = "auth-code-1";
        String subject = "customer-42";
        String deviceId = "device-1";

        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=" + deviceId);
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String nonce = extractQueryParam(location, "nonce");
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, subject, nonce, deviceId);

        Response callbackResponse = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(200, callbackResponse.statusCode(), "response: " + callbackResponse.asString());
        String sessionToken = callbackResponse.jsonPath().getString("sessionToken");
        assertEquals(subject, SignedJWT.parse(sessionToken).getJWTClaimsSet().getSubject());
        assertEquals(deviceId, SignedJWT.parse(sessionToken).getJWTClaimsSet().getStringClaim("device_id"));
        assertEquals(MockOidcProvider.refreshTokenFor(code), callbackResponse.jsonPath().getString("refreshToken"));
        assertNotNull(callbackResponse.jsonPath().getString("refreshTokenExpiry"));
        assertTrue(CORE_SERVICE.getAllServeEvents().isEmpty(), "login must not call core-service");
    }

    @Test
    @DisplayName("returns no tokens and calls core-service for nothing when the provider denies authentication")
    void returnsNoTokensWhenTheProviderDeniesAuthentication() {
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-1");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");

        Response callbackResponse = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("error", "access_denied")
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(401, callbackResponse.statusCode());
        CORE_SERVICE.verify(0, postRequestedFor(urlPathEqualTo("/internal/auth/mobile/devices/device-1/refresh-tokens")));
    }

    @Test
    @DisplayName("asks the provider for PKCE and exactly the mobile channel's scopes")
    void asksTheProviderForPkceAndExactlyTheMobileChannelScopes() {
        Set<String> expectedScopes = new HashSet<>(Channel.MOBILE.scopes());
        expectedScopes.add("openid");
        expectedScopes.add("profile");

        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-1");

        String location = authorizationResponse.getHeader("Location");
        String scope = URLDecoder.decode(extractQueryParam(location, "scope"), StandardCharsets.UTF_8);
        assertEquals(expectedScopes, Set.of(scope.split(" ")));
        assertEquals("S256", extractQueryParam(location, "code_challenge_method"));
        assertEquals("device-1", extractQueryParam(location, "device_id"));
    }

    @Test
    @DisplayName("exchanges the code as the confidential client, with a PKCE verifier and its client secret")
    void exchangesTheCodeAsTheConfidentialClientWithAPkceVerifierAndItsClientSecret() {
        String code = "auth-code-5";
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-5");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, "customer-42", extractQueryParam(location, "nonce"), "device-5");

        Response callbackResponse = given()
                .cookie("JSESSIONID", authorizationResponse.getCookie("JSESSIONID"))
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(200, callbackResponse.statusCode(), callbackResponse.asString());
        String authorization = OIDC_PROVIDER.tokenRequests().getFirst().getHeader("Authorization");
        assertTrue(authorization.startsWith("Basic "));
        assertTrue(OIDC_PROVIDER.tokenRequests().getFirst().getBodyAsString().contains("code_verifier="));
    }

    @Test
    @DisplayName("fails the login when a correctly signed ID token comes from an unexpected issuer")
    void failsTheLoginWhenACorrectlySignedIdTokenComesFromAnUnexpectedIssuer() {
        String code = "auth-code-3";
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-3");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        OIDC_PROVIDER.stubTokenExchangeFromForeignIssuer(code, "customer-42", extractQueryParam(location, "nonce"));

        Response callbackResponse = given()
                .cookie("JSESSIONID", authorizationResponse.getCookie("JSESSIONID"))
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(401, callbackResponse.statusCode());
        assertNoTokensIn(callbackResponse);
        CORE_SERVICE.verify(0, postRequestedFor(urlPathEqualTo("/internal/auth/mobile/devices/device-3/refresh-tokens")));
    }

    @Test
    @DisplayName("issues no second session when an already-processed callback is replayed")
    void issuesNoSecondSessionWhenAnAlreadyProcessedCallbackIsReplayed() {
        String code = "auth-code-6";
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-6");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, "customer-42", extractQueryParam(location, "nonce"), "device-6");
        given().cookie("JSESSIONID", jsessionId).queryParam("code", code).queryParam("state", state)
                .redirects().follow(false).when().get("/login/oauth2/code/oidc");

        Response replay = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(401, replay.statusCode());
        assertNoTokensIn(replay);
        assertEquals(1, OIDC_PROVIDER.tokenRequests().size());
    }

    @Test
    @DisplayName("answers 503 with no tokens when the authorization server cannot be reached")
    void answers503WithNoTokensWhenTheAuthorizationServerCannotBeReached() {
        OIDC_PROVIDER.stubUnreachableTokenEndpoint();
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-7");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);

        Response callbackResponse = given()
                .cookie("JSESSIONID", authorizationResponse.getCookie("JSESSIONID"))
                .queryParam("code", "auth-code-7")
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(503, callbackResponse.statusCode());
        assertEquals("application/problem+json", callbackResponse.getContentType());
        assertNoTokensIn(callbackResponse);
        assertEquals(1, OIDC_PROVIDER.tokenRequests().size());
    }

    private static void assertNoTokensIn(Response response) {
        String body = response.asString();
        assertNull(body.isBlank() ? null : response.jsonPath().getString("sessionToken"), body);
        assertNull(body.isBlank() ? null : response.jsonPath().getString("refreshToken"), body);
    }

    private static String extractQueryParam(String url, String param) {
        Pattern pattern = Pattern.compile("[?&]" + param + "=([^&]+)");
        Matcher matcher = pattern.matcher(URI.create(url).getRawQuery() == null ? "" : url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new AssertionError("query param " + param + " not found in " + url);
    }
}
