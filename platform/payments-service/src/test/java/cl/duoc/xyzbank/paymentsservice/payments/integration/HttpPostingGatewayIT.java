package cl.duoc.xyzbank.paymentsservice.payments.integration;

import cl.duoc.xyzbank.paymentsservice.payments.application.CoreUnavailableException;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingGateway;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingOutcome;
import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import cl.duoc.xyzbank.paymentsservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.paymentsservice.testsupport.TestCoreService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The HTTP posting gateway")
class HttpPostingGatewayIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. A 201 is APPLIED; the request carries payments-service's bearer token and the transfer's two entries
     * 2. A 422 or 409 is REFUSED after exactly one call
     * 3. A 503 followed by a 201 is APPLIED after a retry
     * 4. A persistent 503 throws CoreUnavailableException after three attempts
     * 5. An open circuit throws CoreUnavailableException without calling core-service
     */

    private static final String POSTINGS = "/internal/postings";

    @Autowired
    private PostingGateway gateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("applies a posting with payments-service's token and the transfer's entries")
    void appliesAPostingWithPaymentsServicesTokenAndTheTransfersEntries() {
        coreAnswers(201);
        Payment transfer = aTransfer();

        assertEquals(PostingOutcome.APPLIED, gateway.post(transfer));

        TestCoreService.server().verify(1, postRequestedFor(urlEqualTo(POSTINGS))
                .withHeader("Authorization", equalTo("Bearer payments-service-token"))
                .withRequestBody(equalToJson("""
                        {"paymentId": "%s", "entries": [
                          {"accountId": "%s", "direction": "DEBIT", "amount": 100.00, "currency": "USD"},
                          {"accountId": "%s", "direction": "CREDIT", "amount": 100.00, "currency": "USD"}]}
                        """.formatted(transfer.id(), transfer.sourceAccountId().orElseThrow(),
                                transfer.destinationAccountId().orElseThrow()))));
    }

    @ParameterizedTest
    @ValueSource(ints = {422, 409})
    @DisplayName("returns REFUSED for a refusal after exactly one call")
    void returnsRefusedForARefusalAfterExactlyOneCall(int status) {
        coreAnswers(status);

        assertEquals(PostingOutcome.REFUSED, gateway.post(aTransfer()));

        TestCoreService.server().verify(1, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    @Test
    @DisplayName("retries a 503 and applies the posting")
    void retriesA503AndAppliesThePosting() {
        TestCoreService.server().stubFor(post(urlEqualTo(POSTINGS)).inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        TestCoreService.server().stubFor(post(urlEqualTo(POSTINGS)).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(201)));

        assertEquals(PostingOutcome.APPLIED, gateway.post(aTransfer()));

        TestCoreService.server().verify(2, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    @Test
    @DisplayName("throws CoreUnavailableException after three failed attempts")
    void throwsCoreUnavailableExceptionAfterThreeFailedAttempts() {
        coreAnswers(503);

        assertThrows(CoreUnavailableException.class, () -> gateway.post(aTransfer()));

        TestCoreService.server().verify(3, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    @Test
    @DisplayName("throws CoreUnavailableException without calling core-service when the circuit is open")
    void throwsCoreUnavailableExceptionWhenTheCircuitIsOpen() {
        coreAnswers(201);
        circuitBreakerRegistry.circuitBreaker("coreService").transitionToOpenState();

        assertThrows(CoreUnavailableException.class, () -> gateway.post(aTransfer()));

        TestCoreService.server().verify(0, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    private static void coreAnswers(int status) {
        TestCoreService.server().stubFor(post(urlEqualTo(POSTINGS)).willReturn(aResponse().withStatus(status)));
    }

    private static Payment aTransfer() {
        return Payment.create(UUID.randomUUID(), PaymentType.TRANSFER, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", "gw-" + UUID.randomUUID(), Instant.now());
    }
}
