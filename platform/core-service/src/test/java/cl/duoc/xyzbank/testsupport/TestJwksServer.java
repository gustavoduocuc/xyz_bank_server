package cl.duoc.xyzbank.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Publishes TestAccessTokens' public key the way auth-server publishes its JWKS, so
 * core-service verifies test tokens through its real JWKS-based verifier.
 */
public final class TestJwksServer {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        SERVER.start();
        SERVER.stubFor(get(urlPathEqualTo("/oauth2/jwks")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(TestAccessTokens.jwksJson())));
    }

    private TestJwksServer() {
    }

    public static String jwkSetUri() {
        return SERVER.baseUrl() + "/oauth2/jwks";
    }
}
