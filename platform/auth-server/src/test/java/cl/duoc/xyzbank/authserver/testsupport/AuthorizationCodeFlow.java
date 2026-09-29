package cl.duoc.xyzbank.authserver.testsupport;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/**
 * Drives the browser side of an OAuth 2.0 authorization-code + PKCE login against a running
 * auth-server over HTTPS -- authorization request, form login, back to the authorization
 * endpoint -- and the client side of the token exchange, the way bff-web/bff-mobile and a
 * browser would. Redirects are never followed automatically so each hop can be asserted.
 */
public final class AuthorizationCodeFlow {

    public static final String WEB_CLIENT_ID = "bff-web";
    public static final String WEB_CLIENT_SECRET = "bff-web-dev-secret";
    public static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    public static final String MOBILE_CLIENT_ID = "bff-mobile";
    public static final String MOBILE_CLIENT_SECRET = "bff-mobile-dev-secret";
    public static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";
    public static final String WEB_SCOPES =
            "openid profile web:accounts:read web:customers:read web:transactions:read web:interests:read";
    public static final String MOBILE_SCOPES = "openid profile mobile:accounts:read mobile:transactions:read";
    public static final String DEMO_USERNAME = "demo";
    public static final String DEMO_PASSWORD = "demo-password";
    public static final String SESSION_COOKIE = "XYZ_AUTH_SESSION";

    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int port;

    public AuthorizationCodeFlow(int port) {
        this.port = port;
    }

    public RequestSpecification request() {
        return given().relaxedHTTPSValidation().baseUri("https://localhost").port(port).redirects().follow(false);
    }

    public static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String codeChallengeOf(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * A bff-mobile authorization request for the given device, as bff-mobile sends it.
     */
    public static Map<String, String> mobileAuthorizationRequest(String deviceId, String codeVerifier) {
        Map<String, String> parameters =
                authorizationRequest(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI, MOBILE_SCOPES, codeVerifier);
        parameters.put("device_id", deviceId);
        return parameters;
    }

    /**
     * Runs a complete, successful mobile login for the demo customer on the given device and
     * returns the code.
     */
    public String mobileAuthorizationCodeFor(String deviceId, String codeVerifier) {
        Response response = authorize(mobileAuthorizationRequest(deviceId, codeVerifier), DEMO_USERNAME, DEMO_PASSWORD);
        String code = queryParam(response.getHeader("Location"), "code");
        if (code == null) {
            throw new AssertionError("No authorization code; status " + response.statusCode()
                    + ", Location " + response.getHeader("Location") + ", body " + response.asString());
        }
        return code;
    }

    public static Map<String, String> authorizationRequest(
            String clientId, String redirectUri, String scopes, String codeVerifier) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("response_type", "code");
        parameters.put("client_id", clientId);
        parameters.put("redirect_uri", redirectUri);
        parameters.put("scope", scopes);
        parameters.put("state", "state-" + RANDOM.nextInt(1_000_000));
        parameters.put("nonce", "nonce-" + RANDOM.nextInt(1_000_000));
        if (codeVerifier != null) {
            parameters.put("code_challenge", codeChallengeOf(codeVerifier));
            parameters.put("code_challenge_method", "S256");
        }
        return parameters;
    }

    /**
     * Sends the authorization request as a browser would, logging in as the given user when
     * the server asks for it, and returns the final response of the authorization endpoint
     * (a redirect to the client carrying a code or an error, or an error page).
     */
    public Response authorize(Map<String, String> authorizationRequest, String username, String password) {
        Response first = request().accept(ContentType.HTML).queryParams(authorizationRequest).get("/oauth2/authorize");
        if (!redirectsToLogin(first)) {
            return first;
        }
        String session = first.getCookie(SESSION_COOKIE);
        Response loggedIn = login(session, username, password);
        String authenticatedSession = loggedIn.getCookie(SESSION_COOKIE) != null
                ? loggedIn.getCookie(SESSION_COOKIE)
                : session;
        return request().accept(ContentType.HTML)
                .cookie(SESSION_COOKIE, authenticatedSession)
                .queryParams(authorizationRequest)
                .get("/oauth2/authorize");
    }

    public Response login(String session, String username, String password) {
        Response loginPage = request().accept(ContentType.HTML).cookie(SESSION_COOKIE, session).get("/login");
        Matcher csrf = CSRF_INPUT.matcher(loginPage.asString());
        if (!csrf.find()) {
            throw new AssertionError("No CSRF token on the login page: " + loginPage.asString());
        }
        return request().accept(ContentType.HTML)
                .cookie(SESSION_COOKIE, session)
                .contentType(ContentType.URLENC)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("_csrf", csrf.group(1))
                .post("/login");
    }

    /**
     * Runs a complete, successful login for the demo customer and returns the code.
     */
    public String authorizationCodeFor(String clientId, String redirectUri, String scopes, String codeVerifier) {
        Response response = authorize(
                authorizationRequest(clientId, redirectUri, scopes, codeVerifier), DEMO_USERNAME, DEMO_PASSWORD);
        String code = queryParam(response.getHeader("Location"), "code");
        if (code == null) {
            throw new AssertionError("No authorization code; status " + response.statusCode()
                    + ", Location " + response.getHeader("Location") + ", body " + response.asString());
        }
        return code;
    }

    /**
     * Exchanges a code as bff-web. A null secret presents the client id alone, as a public
     * client would -- the way to try skipping bff-web's secret.
     */
    public Response exchangeAsWebClient(String code, String codeVerifier, String clientSecret) {
        RequestSpecification request = request().contentType(ContentType.URLENC);
        request = clientSecret == null
                ? request.formParam("client_id", WEB_CLIENT_ID)
                : request.auth().preemptive().basic(WEB_CLIENT_ID, clientSecret);
        return request.formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", WEB_REDIRECT_URI)
                .formParam("code_verifier", codeVerifier)
                .post("/oauth2/token");
    }

    public Response exchangeAsMobileClient(String code, String codeVerifier) {
        return request().contentType(ContentType.URLENC)
                .auth().preemptive().basic(MOBILE_CLIENT_ID, MOBILE_CLIENT_SECRET)
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", MOBILE_REDIRECT_URI)
                .formParam("code_verifier", codeVerifier)
                .post("/oauth2/token");
    }

    public static String queryParam(String url, String name) {
        if (url == null) {
            return null;
        }
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair[0].equals(name) && pair.length == 2)
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    private static boolean redirectsToLogin(Response response) {
        String location = response.getHeader("Location");
        return response.statusCode() == 302 && location != null && URI.create(location).getPath().equals("/login");
    }
}
