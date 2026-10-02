package cl.duoc.xyzbank.bffatm.withdrawal.integration;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "core-service.read-timeout-ms=500",
        "resilience4j.retry.instances.coreServiceWithdrawal.waitDuration=10ms"
})
@DisplayName("The withdrawal adapter's retry")
class WithdrawalRetryIT {

    /*
     * Cases (bff-resilience spec, "A retried withdrawal reuses the terminal's Idempotency-Key
     * and debits once"):
     * 1. A first attempt that exceeds the read timeout is retried once with the same
     *    Idempotency-Key and the same body, and the terminal gets the successful result
     * 2. A withdrawal core-service refuses with a 4xx is sent exactly once and the error
     *    surfaces unchanged
     * 3. A withdrawal that keeps failing is attempted at most twice
     */

    private static final String WITHDRAWAL_URL = "/internal/accounts/account-1/withdrawals";
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AuthServerStub AUTH_SERVER = new AuthServerStub();

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        AUTH_SERVER.register(registry);
    }

    @Autowired
    private WithdrawalsPort withdrawals;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private final WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), "USD");

    @BeforeEach
    void resetDownstreams() {
        CORE_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopDownstreams() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    @Test
    @DisplayName("retries a timed-out withdrawal once with the same key and body")
    void retriesATimedOutWithdrawalOnceWithTheSameKeyAndBody() {
        CORE_SERVICE.stubFor(post(urlEqualTo(WITHDRAWAL_URL)).inScenario("slow commit")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(successfulWithdrawal().withFixedDelay(1_500))
                .willSetStateTo("answering"));
        CORE_SERVICE.stubFor(post(urlEqualTo(WITHDRAWAL_URL)).inScenario("slow commit")
                .whenScenarioStateIs("answering")
                .willReturn(successfulWithdrawal()));

        WithdrawalResponse response = withdrawals.withdraw("account-1", request, "terminal-key-1");

        assertEquals("tx-1", response.transactionId());
        List<LoggedRequest> attempts = CORE_SERVICE.findAll(postRequestedFor(urlEqualTo(WITHDRAWAL_URL)));
        assertEquals(2, attempts.size());
        for (LoggedRequest attempt : attempts) {
            assertEquals("terminal-key-1", attempt.getHeader("Idempotency-Key"));
            assertEquals(attempts.get(0).getBodyAsString(), attempt.getBodyAsString());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 422})
    @DisplayName("sends a withdrawal core-service refuses exactly once")
    void sendsAWithdrawalCoreServiceRefusesExactlyOnce(int status) {
        CORE_SERVICE.stubFor(post(urlEqualTo(WITHDRAWAL_URL)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"detail\":\"Refused\"}")));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> withdrawals.withdraw("account-1", request, "terminal-key-2"));

        assertEquals(status, failure.getStatus());
        CORE_SERVICE.verify(1, postRequestedFor(urlEqualTo(WITHDRAWAL_URL)));
    }

    @Test
    @DisplayName("attempts a failing withdrawal at most twice")
    void attemptsAFailingWithdrawalAtMostTwice() {
        CORE_SERVICE.stubFor(post(urlEqualTo(WITHDRAWAL_URL)).willReturn(aResponse().withStatus(503)));

        CoreServiceCallException failure = assertThrows(
                CoreServiceCallException.class, () -> withdrawals.withdraw("account-1", request, "terminal-key-3"));

        assertEquals(503, failure.getStatus());
        CORE_SERVICE.verify(2, postRequestedFor(urlEqualTo(WITHDRAWAL_URL)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder successfulWithdrawal() {
        return aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,"
                        + "\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}");
    }
}
