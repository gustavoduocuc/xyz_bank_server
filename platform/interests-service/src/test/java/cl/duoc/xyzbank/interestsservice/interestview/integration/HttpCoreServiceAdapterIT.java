package cl.duoc.xyzbank.interestsservice.interestview.integration;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.infrastructure.adapters.HttpCoreServiceAdapter;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The core-service HTTP adapter")
class HttpCoreServiceAdapterIT {

    private WireMockServer wireMockServer;
    private HttpCoreServiceAdapter adapter;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        RestClient coreServiceClient = RestClient.builder().baseUrl(wireMockServer.baseUrl()).build();
        ServiceTokenPort serviceTokenPort = () -> "service-jwt";
        adapter = new HttpCoreServiceAdapter(coreServiceClient, serviceTokenPort);
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

        InterestSummaryResponse response = adapter.fetchInterestSummary("account-1", "2026");

        assertEquals(
                new InterestSummaryResponse(
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
    @DisplayName("raises not-found when the summary is missing")
    void raisesNotFoundWhenTheSummaryIsMissing() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/account-1/interest-summary?year=1999"))
                .willReturn(aResponse().withStatus(404)));

        DomainException exception = assertThrows(
                DomainException.class, () -> adapter.fetchInterestSummary("account-1", "1999"));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("posts an interest credit with the service token and Idempotency-Key")
    void postsAnInterestCreditWithTheServiceTokenAndIdempotencyKey() {
        wireMockServer.stubFor(post(urlPathEqualTo("/internal/accounts/account-1/interest-credits"))
                .withHeader("Authorization", equalTo("Bearer service-jwt"))
                .withHeader("Idempotency-Key", equalTo("interest-account-1-2026"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"year\":2026,\"amount\":35.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-15\",\"newBalance\":1035.00}")));

        var response = adapter.creditInterest(
                new cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand(
                        "account-1",
                        2026,
                        new BigDecimal("35.00"),
                        "USD",
                        new BigDecimal("0.035"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1035.00"),
                        "interest-account-1-2026"));

        assertEquals("tx-1", response.transactionId());
        assertEquals(new BigDecimal("1035.00"), response.newBalance());
    }
}
