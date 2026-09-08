package cl.duoc.xyzbank.bffatm.withdrawal.integration;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.infrastructure.adapters.HttpWithdrawalsAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The withdrawals core-service adapter")
class HttpWithdrawalsAdapterIT {

    /*
     * Cases:
     * 1. Forwards the Idempotency-Key header unchanged
     * 2. Maps a core-service 409 to CoreServiceCallException
     * 3. A retry reuses Idempotency-Key and X-Correlation-Id
     */

    private WireMockServer wireMockServer;
    private RestClient coreServiceClient;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        coreServiceClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .requestInterceptor(new CorrelationIdClientInterceptor())
                .build();
    }

    @AfterEach
    void stopWireMock() {
        MDC.clear();
        wireMockServer.stop();
    }

    @Test
    @DisplayName("forwards the Idempotency-Key header unchanged")
    void forwardsTheIdempotencyKeyHeaderUnchanged() {
        stubSuccessfulWithdrawal();

        new HttpWithdrawalsAdapter(coreServiceClient)
                .withdraw("account-1", new WithdrawalRequest(new BigDecimal("40.00"), "USD"), "key-1");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withHeader("Idempotency-Key", equalTo("key-1")));
    }

    @Test
    @DisplayName("maps a core-service 409 response to CoreServiceCallException with status 409")
    void mapsAConflictResponseToCoreServiceCallException() {
        wireMockServer.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Conflict\"}")));

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> new HttpWithdrawalsAdapter(coreServiceClient)
                        .withdraw("account-1", new WithdrawalRequest(new BigDecimal("40.00"), "USD"), "key-1"));

        assertEquals(409, exception.getStatus());
    }

    @Test
    @DisplayName("forwards the same Idempotency-Key and correlation id on retry")
    void forwardsTheSameIdempotencyKeyAndCorrelationIdOnRetry() {
        stubSuccessfulWithdrawal();
        MDC.put("correlationId", "corr-atm-1");
        HttpWithdrawalsAdapter adapter = new HttpWithdrawalsAdapter(coreServiceClient);
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), "USD");

        adapter.withdraw("account-1", request, "key-1");
        adapter.withdraw("account-1", request, "key-1");

        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withHeader("Idempotency-Key", equalTo("key-1"))
                .withHeader("X-Correlation-Id", equalTo("corr-atm-1")));
    }

    private void stubSuccessfulWithdrawal() {
        wireMockServer.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));
    }
}
