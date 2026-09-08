package cl.duoc.xyzbank.bffweb.interestview.integration;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.infrastructure.adapters.HttpInterestAdapter;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The interest core-service adapter")
class HttpInterestAdapterIT {

    /*
     * Cases:
     * 1. Maps a 200 interest summary
     * 2. Raises CoreServiceCallException(404) on a missing summary
     */

    private WireMockServer wireMockServer;
    private RestClient coreServiceClient;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        coreServiceClient = RestClient.builder().baseUrl(wireMockServer.baseUrl()).build();
    }

    @AfterEach
    void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("maps a 200 interest-summary response")
    void mapsA200InterestSummaryResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/account-1/interest-summary?year=2026"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"accountId\":\"account-1\",\"year\":2026,\"openingBalance\":1000.00,\"closingBalance\":1100.00,\"interestRate\":0.05,\"interestAmount\":50.00,\"currency\":\"USD\"}")));

        InterestViewResponse response = new HttpInterestAdapter(coreServiceClient).fetchSummary("account-1", "2026");

        assertEquals(
                new InterestViewResponse(
                        "account-1",
                        2026,
                        new BigDecimal("1000.00"),
                        new BigDecimal("1100.00"),
                        new BigDecimal("0.05"),
                        new BigDecimal("50.00"),
                        "USD"),
                response);
    }

    @Test
    @DisplayName("raises CoreServiceCallException(404) when the summary is missing")
    void raisesCoreServiceCallExceptionWhenTheSummaryIsMissing() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/account-1/interest-summary?year=1999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"No interest summary\"}")));

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> new HttpInterestAdapter(coreServiceClient).fetchSummary("account-1", "1999"));

        assertEquals(404, exception.getStatus());
    }
}
