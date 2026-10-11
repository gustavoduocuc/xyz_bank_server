package cl.duoc.xyzbank.paymentsservice.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * One stub for core-service's postings endpoint and auth-server's token endpoint, shared by every
 * test context so they are all configured alike and cached.
 */
public final class TestCoreService {

    public static final String TOKEN_PATH = "/oauth2/token";

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
    }

    private TestCoreService() {
    }

    public static WireMockServer server() {
        return SERVER;
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    /** Clears the stubs and the request log, keeping a token endpoint that issues a client_credentials token. */
    public static void reset() {
        SERVER.resetAll();
        SERVER.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"payments-service-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
    }
}
