package cl.duoc.xyzbank.bffatm.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** A token endpoint that issues one fixed client token for bff-atm's outbound calls. */
public final class AuthServerStub {

    public static final String CLIENT_ID = "bff-atm";
    public static final String CLIENT_SECRET = "bff-atm-dev-secret";
    public static final String ACCESS_TOKEN = "bff-atm-access-token";

    private final WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());

    public void start() {
        server.start();
        server.stubFor(post(urlPathEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"expires_in\":900,\"token_type\":\"Bearer\"}")));
    }

    public void stop() {
        server.stop();
    }

    public String tokenUri() {
        return server.baseUrl() + "/oauth2/token";
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("auth-server.token-uri", this::tokenUri);
        registry.add("auth-server.client-id", () -> CLIENT_ID);
        registry.add("auth-server.client-secret", () -> CLIENT_SECRET);
    }
}
