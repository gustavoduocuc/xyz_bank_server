package cl.duoc.xyzbank.bffweb.shared.integration;

import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "resilience4j.retry.instances.coreServiceRead.waitDuration=10ms")
@DisplayName("bff-web's core-service reads under failure")
class CoreServiceReadResilienceIT {

    /*
     * Cases, for each core-service read adapter (profile, accounts, dashboard transactions,
     * transaction history) -- bff-resilience spec, "Only idempotent operations are retried, and
     * never on a client error":
     * 1. A transient 503 is retried and the read succeeds
     * 2. A 404 is sent exactly once and surfaces unchanged
     * 3. A persistent 503 stops after the configured 3 attempts with core-service unavailable
     */

    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
    }

    @DynamicPropertySource
    static void coreService(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @Autowired
    private CustomerProfilePort profiles;

    @Autowired
    private AccountsPort accounts;

    @Autowired
    private cl.duoc.xyzbank.bffweb.dashboard.application.ports.TransactionsPort latestTransactions;

    @Autowired
    @Qualifier("transactionHistoryHttpAdapter")
    private cl.duoc.xyzbank.bffweb.transactionhistory.application.ports.TransactionsPort history;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetCoreService() {
        CORE_SERVICE.resetAll();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopCoreService() {
        CORE_SERVICE.stop();
    }

    record Read(String name, String path, String body, Consumer<CoreServiceReadResilienceIT> call) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Read> reads() {
        String page = "{\"items\":[],\"nextCursor\":null}";
        return Stream.of(
                new Read("profile", "/internal/customers/customer-1",
                        "{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}",
                        test -> test.profiles.fetchProfile("customer-1")),
                new Read("accounts", "/internal/customers/customer-1/accounts", "[]",
                        test -> test.accounts.fetchAccountsForCustomer("customer-1")),
                new Read("dashboard transactions", "/internal/accounts/account-1/transactions", page,
                        test -> test.latestTransactions.fetchLatestTransactions("account-1", 5)),
                new Read("transaction history", "/internal/accounts/account-1/transactions", page,
                        test -> test.history.fetchHistory("account-1", null, null, null, null, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reads")
    @DisplayName("retries a transient 503")
    void retriesATransient503(Read read) {
        CORE_SERVICE.stubFor(get(urlPathEqualTo(read.path())).inScenario("restart")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        CORE_SERVICE.stubFor(get(urlPathEqualTo(read.path())).inScenario("restart")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(read.body())));

        read.call().accept(this);

        CORE_SERVICE.verify(2, getRequestedFor(urlPathEqualTo(read.path())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reads")
    @DisplayName("sends a read answered 404 exactly once")
    void sendsAReadAnswered404ExactlyOnce(Read read) {
        CORE_SERVICE.stubFor(get(urlPathEqualTo(read.path())).willReturn(aResponse().withStatus(404)));

        CoreServiceCallException failure = assertThrows(CoreServiceCallException.class, () -> read.call().accept(this));

        assertEquals(404, failure.getStatus());
        CORE_SERVICE.verify(1, getRequestedFor(urlPathEqualTo(read.path())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reads")
    @DisplayName("stops after three attempts when core-service stays unavailable")
    void stopsAfterThreeAttemptsWhenCoreServiceStaysUnavailable(Read read) {
        CORE_SERVICE.stubFor(get(urlPathEqualTo(read.path())).willReturn(aResponse().withStatus(503)));

        CoreServiceCallException failure = assertThrows(CoreServiceCallException.class, () -> read.call().accept(this));

        assertEquals(503, failure.getStatus());
        CORE_SERVICE.verify(3, getRequestedFor(urlPathEqualTo(read.path())));
    }
}
