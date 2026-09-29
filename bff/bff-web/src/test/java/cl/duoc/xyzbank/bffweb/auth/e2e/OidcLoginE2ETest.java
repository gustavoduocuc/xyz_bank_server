package cl.duoc.xyzbank.bffweb.auth.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-web's OIDC login")
class OidcLoginE2ETest {

    /*
     * Cases:
     * 1. A successful OIDC callback sets an HttpOnly/Secure/SameSite session cookie carrying
     *    the web channel's full scope set, and obtains a refresh token from core-service
     *    using only the service credential (no user token yet)
     * 2. A callback where the provider denied authentication (error param) sets no cookie
     *    and never calls core-service
     * 3. A callback whose token exchange fails sets no cookie and never calls core-service's
     *    refresh-token endpoint
     * 4. The authorization redirect asks for PKCE (S256) and exactly openid, profile and the web
     *    channel's scope set -- nothing the authorization server would refuse to a web client
     * 5. A correctly signed ID token from an unexpected issuer fails the login: no cookie, and
     *    core-service is never called
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
    @DisplayName("sets the session cookie and obtains the first refresh token on a successful callback")
    void setsTheSessionCookieAndObtainsTheFirstRefreshTokenOnASuccessfulCallback() {
        String code = "auth-code-1";
        String subject = "customer-42";
        CORE_SERVICE.stubFor(post(urlPathEqualTo("/internal/auth/web/refresh-tokens"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson(
                        "{\"customerId\":\"" + subject + "\",\"refreshToken\":null}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"refreshToken\":\"opaque-refresh-1\",\"expiry\":\"2099-01-01T00:00:00Z\"}")));

        Response authorizationResponse =
                given().redirects().follow(false).when().get("/oauth2/authorization/oidc");
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

        List<String> setCookieHeaders = callbackResponse.getHeaders().getValues("Set-Cookie");
        String sessionCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith("session="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no session cookie set; response: " + callbackResponse.asString()
                        + " status: " + callbackResponse.statusCode()));
        String refreshCookie = setCookieHeaders.stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no refresh_token cookie set"));

        assertTrue(sessionCookie.contains("HttpOnly"), "session cookie must be HttpOnly: " + sessionCookie);
        assertTrue(sessionCookie.contains("Secure"), "session cookie must be Secure: " + sessionCookie);
        assertTrue(sessionCookie.contains("SameSite"), "session cookie must carry SameSite: " + sessionCookie);
        assertTrue(refreshCookie.contains("opaque-refresh-1"));

        CORE_SERVICE.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        urlPathEqualTo("/internal/auth/web/refresh-tokens"))
                .withHeader("X-Service-Credential", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                        "dev-service-credential-web")));
    }

    @Test
    @DisplayName("sets no cookie and calls core-service for nothing when the provider denies authentication")
    void setsNoCookieWhenTheProviderDeniesAuthentication() {
        Response authorizationResponse =
                given().redirects().follow(false).when().get("/oauth2/authorization/oidc");
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

        assertNoSessionOrRefreshCookieSet(callbackResponse);
        CORE_SERVICE.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/internal/auth/web/refresh-tokens")));
    }

    @Test
    @DisplayName("sets no cookie and never calls core-service's refresh-token endpoint when the token exchange fails")
    void setsNoCookieWhenTheTokenExchangeFails() {
        String code = "auth-code-2";
        OIDC_PROVIDER.stubFailedTokenExchange(code);

        Response authorizationResponse =
                given().redirects().follow(false).when().get("/oauth2/authorization/oidc");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");

        Response callbackResponse = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertNoSessionOrRefreshCookieSet(callbackResponse);
        CORE_SERVICE.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/internal/auth/web/refresh-tokens")));
    }

    @Test
    @DisplayName("asks the provider for PKCE and exactly the web channel's scopes")
    void asksTheProviderForPkceAndExactlyTheWebChannelScopes() {
        Response authorizationResponse =
                given().redirects().follow(false).when().get("/oauth2/authorization/oidc");
        String location = authorizationResponse.getHeader("Location");

        Set<String> expectedScopes = new HashSet<>(Channel.WEB.scopes());
        expectedScopes.add("openid");
        expectedScopes.add("profile");
        String scope = URLDecoder.decode(extractQueryParam(location, "scope"), StandardCharsets.UTF_8);
        assertEquals(expectedScopes, Set.of(scope.split(" ")));
        assertEquals("S256", extractQueryParam(location, "code_challenge_method"));
    }

    @Test
    @DisplayName("fails the login when a correctly signed ID token comes from an unexpected issuer")
    void failsTheLoginWhenACorrectlySignedIdTokenComesFromAnUnexpectedIssuer() {
        String code = "auth-code-3";
        Response authorizationResponse =
                given().redirects().follow(false).when().get("/oauth2/authorization/oidc");
        String location = authorizationResponse.getHeader("Location");
        String state = URLDecoder.decode(extractQueryParam(location, "state"), StandardCharsets.UTF_8);
        String nonce = extractQueryParam(location, "nonce");
        String jsessionId = authorizationResponse.getCookie("JSESSIONID");
        OIDC_PROVIDER.stubSuccessfulTokenExchange(code, "customer-42", nonce, "https://impostor.example");

        Response callbackResponse = given()
                .cookie("JSESSIONID", jsessionId)
                .queryParam("code", code)
                .queryParam("state", state)
                .redirects().follow(false)
                .when()
                .get("/login/oauth2/code/oidc");

        assertEquals(401, callbackResponse.statusCode());
        assertNoSessionOrRefreshCookieSet(callbackResponse);
        CORE_SERVICE.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                urlPathEqualTo("/internal/auth/web/refresh-tokens")));
    }

    private static void assertNoSessionOrRefreshCookieSet(Response response) {
        List<String> setCookieHeaders = response.getHeaders().getValues("Set-Cookie");
        assertTrue(
                setCookieHeaders.stream().noneMatch(header -> header.startsWith("session=")),
                "expected no session cookie, got: " + setCookieHeaders);
        assertTrue(
                setCookieHeaders.stream().noneMatch(header -> header.startsWith("refresh_token=")),
                "expected no refresh_token cookie, got: " + setCookieHeaders);
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
