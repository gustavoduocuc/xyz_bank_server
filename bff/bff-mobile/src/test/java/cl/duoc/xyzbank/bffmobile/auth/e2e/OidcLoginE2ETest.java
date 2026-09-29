package cl.duoc.xyzbank.bffmobile.auth.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-mobile's OIDC login")
class OidcLoginE2ETest {

    /*
     * Cases:
     * 1. A successful OIDC callback with a supplied device identifier returns a device-bound
     *    session token and a refresh token directly in the response body (no cookie), and
     *    registers the device with core-service
     * 2. A callback where the provider denied authentication returns no tokens and never
     *    calls core-service
     * 3. The authorization redirect asks for PKCE (S256) and exactly openid, profile and the
     *    mobile channel's scope set
     * 4. The code is exchanged as a public client: PKCE verifier, no client secret
     * 5. A correctly signed ID token from an unexpected issuer fails the login: no tokens in the
     *    body, and core-service is never called
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
    @DisplayName("returns a device-bound session and registers the device on a successful callback")
    void returnsADeviceBoundSessionAndRegistersTheDeviceOnASuccessfulCallback() {
        String code = "auth-code-1";
        String subject = "customer-42";
        String deviceId = "device-1";
        CORE_SERVICE.stubFor(post(urlPathEqualTo("/internal/auth/mobile/devices/" + deviceId + "/refresh-tokens"))
                .withRequestBody(equalTo("{\"customerId\":\"" + subject + "\",\"refreshToken\":null}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"customerId\":\"" + subject + "\",\"refreshToken\":\"opaque-refresh-1\","
                                + "\"expiry\":\"2099-01-01T00:00:00Z\"}")));

        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=" + deviceId);
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String nonce = extractQueryParam(location, "nonce");
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, subject, nonce);

        Response callbackResponse = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(200, callbackResponse.statusCode(), "response: " + callbackResponse.asString());
        String sessionToken = callbackResponse.jsonPath().getString("sessionToken");
        String refreshToken = callbackResponse.jsonPath().getString("refreshToken");
        assertNotNull(sessionToken);
        assertEquals("opaque-refresh-1", refreshToken);

        CORE_SERVICE.verify(postRequestedFor(urlPathEqualTo("/internal/auth/mobile/devices/" + deviceId + "/refresh-tokens"))
                .withHeader("X-Service-Credential", equalTo("dev-service-credential-mobile")));
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
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-1");
        String location = authorizationResponse.getHeader("Location");

        Set<String> expectedScopes = new HashSet<>(Channel.MOBILE.scopes());
        expectedScopes.add("openid");
        expectedScopes.add("profile");
        String scope = URLDecoder.decode(extractQueryParam(location, "scope"), StandardCharsets.UTF_8);
        assertEquals(expectedScopes, Set.of(scope.split(" ")));
        assertEquals("S256", extractQueryParam(location, "code_challenge_method"));
    }

    @Test
    @DisplayName("exchanges the code as a public client, with a PKCE verifier and no client secret")
    void exchangesTheCodeAsAPublicClientWithAPkceVerifierAndNoClientSecret() {
        String code = "auth-code-5";
        CORE_SERVICE.stubFor(post(urlPathEqualTo("/internal/auth/mobile/devices/device-5/refresh-tokens"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"customerId\":\"customer-42\",\"refreshToken\":\"opaque-refresh-5\","
                                + "\"expiry\":\"2099-01-01T00:00:00Z\"}")));
        Response authorizationResponse = given()
                .redirects().follow(false)
                .when()
                .get("/oauth2/authorization/oidc?deviceId=device-5");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, "customer-42", extractQueryParam(location, "nonce"));

        Response callbackResponse = given()
                .cookie("JSESSIONID", authorizationResponse.getCookie("JSESSIONID"))
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(200, callbackResponse.statusCode(), callbackResponse.asString());
        OIDC_PROVIDER.verifyTokenExchangeWithoutClientSecret();
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
        OIDC_PROVIDER.stubSuccessfulTokenExchange(
                code, "customer-42", extractQueryParam(location, "nonce"), "https://impostor.example");

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
