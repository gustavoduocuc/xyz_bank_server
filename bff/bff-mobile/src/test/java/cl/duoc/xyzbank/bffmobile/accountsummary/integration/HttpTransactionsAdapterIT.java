package cl.duoc.xyzbank.bffmobile.accountsummary.integration;

import cl.duoc.xyzbank.bffmobile.accountsummary.infrastructure.adapters.HttpTransactionsAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@DisplayName("The mobile transactions adapter")
class HttpTransactionsAdapterIT {

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
    @DisplayName("always requests pageSize=5 and no other filter parameter")
    void alwaysRequestsFixedPageSizeAndNoOtherFilter() {
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/accounts/account-1/transactions"))
                .withQueryParam("pageSize", equalTo("5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[],\"nextCursor\":null}")));

        new HttpTransactionsAdapter(coreServiceClient).fetchLatestTransactions("account-1");

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/accounts/account-1/transactions"))
                .withQueryParam("pageSize", equalTo("5"))
                .withoutQueryParam("from")
                .withoutQueryParam("to")
                .withoutQueryParam("type")
                .withoutQueryParam("cursor"));
    }
}
