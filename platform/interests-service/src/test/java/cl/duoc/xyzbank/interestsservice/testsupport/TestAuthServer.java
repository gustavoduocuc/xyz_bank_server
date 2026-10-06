package cl.duoc.xyzbank.interestsservice.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Stands in for auth-server in interests-service's tests: publishes TestAccessTokens' public key
 * as the JWKS and answers interests-service's client_credentials requests with a signed
 * interests token.
 */
public final class TestAuthServer {

    public static final String CLIENT_ID = "interests-service";
    public static final String CLIENT_SECRET = "interests-service-test-secret";

    // Fixed port matching auth.jwk-set-uri / auth.token-uri in the test application.yml, so every
    // application context in the test run finds it without extra configuration
    public static final int PORT = 9997;
    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().port(PORT));

    static {
        SERVER.start();
        stubEndpoints();
    }

    private TestAuthServer() {
    }

    public static String jwkSetUri() {
        return SERVER.baseUrl() + "/oauth2/jwks";
    }

    public static String tokenUri() {
        return SERVER.baseUrl() + "/oauth2/token";
    }

    public static int tokenRequests() {
        return SERVER.findAll(postRequestedFor(urlPathEqualTo("/oauth2/token"))).size();
    }

    public static void reset() {
        SERVER.resetAll();
        stubEndpoints();
    }

    /**
     * The token endpoint answers status to every request until the next reset. Tokens it does
     * issue elsewhere in a resilience test should live only 60 s: inside the adapter's refresh
     * margin, so none is cached between cases.
     */
    public static void tokenEndpointAnswers(int status) {
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"" + (status == 401 ? "invalid_client" : "server_error") + "\"}")));
    }

    /** The token endpoint answers status once, then issues short-lived (uncached) tokens. */
    public static void tokenEndpointFailsOnceWith(int status) {
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token")).inScenario("token outage")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(status))
                .willSetStateTo("recovered"));
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token")).inScenario("token outage")
                .whenScenarioStateIs("recovered")
                .willReturn(shortLivedToken()));
    }

    /** The token endpoint issues short-lived (uncached) tokens. */
    public static void tokenEndpointIssuesShortLivedTokens() {
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(shortLivedToken()));
    }

    /** Every token request has its connection reset, as when auth-server is down. */
    public static void tokenEndpointUnreachable() {
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    private static ResponseDefinitionBuilder shortLivedToken() {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + TestAccessTokens.interests()
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":60,\"scope\":\"interests:write\"}");
    }

    private static void stubEndpoints() {
        SERVER.stubFor(get(urlPathEqualTo("/oauth2/jwks")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(TestAccessTokens.jwksJson())));
        SERVER.stubFor(post(urlPathEqualTo("/oauth2/token")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + TestAccessTokens.interests()
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":900,\"scope\":\"interests:write\"}")));
    }
}
