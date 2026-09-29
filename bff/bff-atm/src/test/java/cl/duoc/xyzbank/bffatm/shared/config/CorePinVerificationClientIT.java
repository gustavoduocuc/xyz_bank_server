package cl.duoc.xyzbank.bffatm.shared.config;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.ClientCredentialsTokenInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The core PIN-verification RestClient bean")
class CorePinVerificationClientIT {

    /*
     * Cases:
     * 1. A call succeeds over TLS, trusting the shared dev CA
     * 2. The same request is never received on the plain-HTTP listener
     */

    private WireMockServer coreService;
    private AuthServerStub authServer;

    @BeforeEach
    void startWireMock() {
        coreService = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .dynamicHttpsPort()
                .keystorePath("tls/keystore.p12")
                .keystorePassword("xyzbank-dev")
                .keyManagerPassword("xyzbank-dev"));
        coreService.start();
        coreService.stubFor(post(urlEqualTo("/internal/auth/atm/pin-verifications"))
                .willReturn(aResponse().withStatus(200)));
        authServer = new AuthServerStub();
        authServer.start();
    }

    @AfterEach
    void stopWireMock() {
        coreService.stop();
        authServer.stop();
    }

    @Test
    @DisplayName("succeeds over TLS and is never received on the plain-http listener")
    void succeedsOverTlsAndIsNeverReceivedOnThePlainHttpListener() throws Exception {
        RestClient client = new CoreServiceClientConfig()
                .corePinVerificationClient(
                        "https://localhost:" + coreService.httpsPort(),
                        3000,
                        3000,
                        "tls/truststore.p12",
                        "xyzbank-dev",
                        new DefaultResourceLoader(),
                        new CorrelationIdClientInterceptor(),
                        new ClientCredentialsTokenInterceptor(
                                RestClient.create(),
                                authServer.tokenUri(),
                                AuthServerStub.CLIENT_ID,
                                AuthServerStub.CLIENT_SECRET,
                                Clock.systemUTC()));

        client.post().uri("/internal/auth/atm/pin-verifications").retrieve().toBodilessEntity();

        coreService.verify(1, postRequestedFor(urlEqualTo("/internal/auth/atm/pin-verifications")));
        List<ServeEvent> events = coreService.getAllServeEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0).getRequest().getAbsoluteUrl().contains(":" + coreService.httpsPort()));
    }
}
