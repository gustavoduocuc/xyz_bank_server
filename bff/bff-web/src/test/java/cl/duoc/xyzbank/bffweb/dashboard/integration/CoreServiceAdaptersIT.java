package cl.duoc.xyzbank.bffweb.dashboard.integration;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.RecentTransaction;
import cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters.HttpAccountsAdapter;
import cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters.HttpCustomerProfileAdapter;
import cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters.HttpTransactionsAdapter;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The core-service http adapters")
class CoreServiceAdaptersIT {

    /*
     * Cases:
     * 1. HttpCustomerProfileAdapter maps a 200 response correctly
     * 2. HttpCustomerProfileAdapter raises CoreServiceCallException(404) on a 404 response
     * 3. HttpAccountsAdapter maps a 200 response correctly
     * 4. HttpAccountsAdapter raises CoreServiceCallException(404) on a 404 response
     * 5. HttpTransactionsAdapter maps a 200 response correctly
     * 6. HttpTransactionsAdapter raises CoreServiceCallException(404) on a 404 response
     * 7. HttpTransactionsAdapter forwards from/to/type/cursor/pageSize unchanged
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
    @DisplayName("HttpCustomerProfileAdapter maps a 200 response correctly")
    void httpCustomerProfileAdapterMapsA200Response() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/customers/customer-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));

        CustomerProfile profile = new HttpCustomerProfileAdapter(coreServiceClient).fetchProfile("customer-1");

        assertEquals(new CustomerProfile("customer-1", "Ana Perez", "ana@example.com"), profile);
    }

    @Test
    @DisplayName("HttpCustomerProfileAdapter raises CoreServiceCallException(404) on a 404 response")
    void httpCustomerProfileAdapterRaisesCoreServiceCallExceptionOn404() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/customers/unknown"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Customer unknown not found\"}")));

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> new HttpCustomerProfileAdapter(coreServiceClient).fetchProfile("unknown"));

        assertEquals(404, exception.getStatus());
    }

    @Test
    @DisplayName("HttpAccountsAdapter maps a 200 response correctly")
    void httpAccountsAdapterMapsA200Response() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/customers/customer-1/accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "[{\"id\":\"account-1\",\"accountNumber\":\"1000000001\",\"balance\":500.00,\"currency\":\"USD\"}]")));

        List<AccountBalance> accounts = new HttpAccountsAdapter(coreServiceClient).fetchAccountsForCustomer("customer-1");

        assertEquals(List.of(new AccountBalance("account-1", "1000000001", new BigDecimal("500.00"), "USD")), accounts);
    }

    @Test
    @DisplayName("HttpAccountsAdapter raises CoreServiceCallException(404) on a 404 response")
    void httpAccountsAdapterRaisesCoreServiceCallExceptionOn404() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/customers/unknown/accounts"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Customer unknown not found\"}")));

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> new HttpAccountsAdapter(coreServiceClient).fetchAccountsForCustomer("unknown"));

        assertEquals(404, exception.getStatus());
    }

    @Test
    @DisplayName("HttpTransactionsAdapter maps a 200 response correctly")
    void httpTransactionsAdapterMapsA200Response() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"items\":[{\"id\":\"tx-1\",\"type\":\"DEBIT\",\"amount\":50.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"description\":null}],\"nextCursor\":null}")));

        List<RecentTransaction> transactions =
                new HttpTransactionsAdapter(coreServiceClient).fetchLatestTransactions("account-1", 5);

        assertEquals(
                List.of(new RecentTransaction("tx-1", "DEBIT", new BigDecimal("50.00"), "USD", "2026-01-01", null)),
                transactions);
    }

    @Test
    @DisplayName("HttpTransactionsAdapter raises CoreServiceCallException(404) on a 404 response")
    void httpTransactionsAdapterRaisesCoreServiceCallExceptionOn404() {
        wireMockServer.stubFor(get(urlEqualTo("/internal/accounts/unknown/transactions?pageSize=5"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Account unknown not found\"}")));

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> new HttpTransactionsAdapter(coreServiceClient).fetchLatestTransactions("unknown", 5));

        assertEquals(404, exception.getStatus());
    }

    @Test
    @DisplayName("HttpTransactionsAdapter forwards from, to, type, cursor and pageSize unchanged")
    void httpTransactionsAdapterForwardsFilterQueryParametersUnchanged() {
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/accounts/account-1/transactions"))
                .withQueryParam("from", equalTo("2026-01-01"))
                .withQueryParam("to", equalTo("2026-01-31"))
                .withQueryParam("type", equalTo("DEBIT"))
                .withQueryParam("cursor", equalTo("cursor-2"))
                .withQueryParam("pageSize", equalTo("20"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[],\"nextCursor\":null}")));

        TransactionHistoryResponse response =
                new cl.duoc.xyzbank.bffweb.transactionhistory.infrastructure.adapters.HttpTransactionsAdapter(
                                coreServiceClient)
                        .fetchHistory("account-1", "2026-01-01", "2026-01-31", "DEBIT", "cursor-2", 20);

        assertEquals(List.of(), response.items());
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/accounts/account-1/transactions"))
                .withQueryParam("from", equalTo("2026-01-01"))
                .withQueryParam("to", equalTo("2026-01-31"))
                .withQueryParam("type", equalTo("DEBIT"))
                .withQueryParam("cursor", equalTo("cursor-2"))
                .withQueryParam("pageSize", equalTo("20")));
    }
}
