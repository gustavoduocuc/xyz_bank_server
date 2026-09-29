package cl.duoc.xyzbank.bffatm.shared.integration;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.ClientCredentialsTokenInterceptor;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The client-credentials token interceptor")
class ClientCredentialsTokenInterceptorIT {

    /*
     * Cases:
     * 1. Every core-service call carries bff-atm's client token and no static credential
     * 2. The token is reused while it is far from expiry
     * 3. A new token is obtained once the cached one is about to expire
     */

    private static final String CLIENT_ID = "bff-atm";
    private static final String CLIENT_SECRET = "bff-atm-dev-secret";
    private static final String CORE_PATH = "/internal/accounts/account-1/balance";
    private static final String TOKEN_PATH = "/oauth2/token";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-29T12:00:00Z"));
    private WireMockServer server;
    private RestClient coreClient;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        server.stubFor(get(urlPathEqualTo(CORE_PATH)).willReturn(aResponse().withStatus(200)));
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .inScenario("tokens")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("rotated")
                .willReturn(tokenResponse("atm-token-1")));
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .inScenario("tokens")
                .whenScenarioStateIs("rotated")
                .willReturn(tokenResponse("atm-token-2")));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        ClientCredentialsTokenInterceptor interceptor = new ClientCredentialsTokenInterceptor(
                RestClient.create(), server.baseUrl() + TOKEN_PATH, CLIENT_ID, CLIENT_SECRET, clock);
        coreClient = RestClient.builder().baseUrl(server.baseUrl()).requestInterceptor(interceptor).build();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("sends bff-atm's client token on every core-service call and no static credential")
    void sendsTheClientTokenOnEveryCoreServiceCallAndNoStaticCredential() {
        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();
        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();

        server.verify(2, getRequestedFor(urlPathEqualTo(CORE_PATH))
                .withHeader("Authorization", equalTo("Bearer atm-token-1"))
                .withoutHeader("X-Service-Credential"));
        server.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withHeader("Authorization", containing("Basic "))
                .withRequestBody(containing("grant_type=client_credentials")));
    }

    @Test
    @DisplayName("reuses the cached token while it is far from expiry")
    void reusesTheCachedTokenWhileItIsFarFromExpiry() {
        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();
        now.set(now.get().plus(Duration.ofMinutes(10)));

        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();

        assertEquals(1, server.findAll(postRequestedFor(urlPathEqualTo(TOKEN_PATH))).size());
    }

    @Test
    @DisplayName("obtains a new token once the cached one is about to expire")
    void obtainsANewTokenOnceTheCachedOneIsAboutToExpire() {
        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();
        now.set(now.get().plus(Duration.ofMinutes(14).plusSeconds(30)));

        coreClient.get().uri(CORE_PATH).retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlPathEqualTo(CORE_PATH))
                .withHeader("Authorization", equalTo("Bearer atm-token-2")));
        assertEquals(2, server.findAll(postRequestedFor(urlPathEqualTo(TOKEN_PATH))).size());
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder tokenResponse(String accessToken) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + accessToken + "\",\"expires_in\":900,\"token_type\":\"Bearer\"}");
    }
}
