package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The account lifecycle endpoints over HTTP")
class AccountLifecycleE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Opens an account with 201 for a customer customers-service knows
     * 2. Repeating the opening's Idempotency-Key returns the same account
     * 3. Answers 422 for a customer customers-service does not know
     * 4. Answers 503 without opening anything when customers-service is down
     * 5. Updates alias and limit with 200 and version 1; the retry with the same key returns 200
     * 6. Answers 409 for an update from a stale version
     * 7. Closes a zero-balance account with 200
     * 8. Answers 409 when closing an account with funds
     * 9. A CLOSED account answers 409 to a withdrawal and to an interest credit
     * 10. Answers 401 without a token and 403 to a token without accounts:write
     */

    private static final WireMockServer CUSTOMERS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CUSTOMERS_SERVICE.start();
    }

    @DynamicPropertySource
    static void customersService(DynamicPropertyRegistry registry) {
        registry.add("customers-service.base-url", CUSTOMERS_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private AtmSessionRepository atmSessionRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private final String customerId = Id.generate().getValue();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        CUSTOMERS_SERVICE.resetAll();
        circuitBreakers.circuitBreaker("customersService").reset();
        CUSTOMERS_SERVICE.stubFor(get(urlPathMatching("/internal/customers/.*")).willReturn(aResponse().withStatus(404)));
        CUSTOMERS_SERVICE.stubFor(get(urlPathMatching("/internal/customers/" + customerId))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + customerId + "\"}")));
    }

    @AfterAll
    static void stopCustomersService() {
        CUSTOMERS_SERVICE.stop();
    }

    @Test
    @DisplayName("opens an account with 201 for a customer customers-service knows")
    void opensAnAccountWith201ForACustomerCustomersServiceKnows() {
        open(key(), customerId).then().statusCode(201)
                .body("customerId", equalTo(customerId))
                .body("balance", equalTo(0.0f))
                .body("currency", equalTo("USD"))
                .body("status", equalTo("ACTIVE"))
                .body("version", equalTo(0));
    }

    @Test
    @DisplayName("repeating the opening's Idempotency-Key returns the same account")
    void repeatingTheOpeningsIdempotencyKeyReturnsTheSameAccount() {
        String key = key();
        String firstId = open(key, customerId).then().statusCode(201).extract().path("id");

        open(key, customerId).then().statusCode(201).body("id", equalTo(firstId));
    }

    @Test
    @DisplayName("answers 422 for a customer customers-service does not know")
    void answers422ForACustomerCustomersServiceDoesNotKnow() {
        String key = key();

        open(key, Id.generate().getValue()).then().statusCode(422).contentType("application/problem+json");

        assertTrue(accountRepository.findByOpeningIdempotencyKey(key).isEmpty());
    }

    @Test
    @DisplayName("answers 503 without opening anything when customers-service is down")
    void answers503WithoutOpeningAnythingWhenCustomersServiceIsDown() {
        CUSTOMERS_SERVICE.stubFor(get(urlPathMatching("/internal/customers/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        String key = key();

        open(key, customerId).then().statusCode(503).contentType("application/problem+json");

        assertTrue(accountRepository.findByOpeningIdempotencyKey(key).isEmpty());
    }

    @Test
    @DisplayName("updates alias and limit with 200 and version 1; the retry with the same key returns 200")
    void updatesAliasAndLimitWith200AndVersion1AndTheRetryWithTheSameKeyReturns200() {
        String accountId = openedAccount();
        Map<String, Object> body = Map.of("alias", "Viajes", "dailyWithdrawalLimit", 800.00, "version", 0);

        patch(accountId, "upd-1", body).then().statusCode(200)
                .body("alias", equalTo("Viajes"))
                .body("dailyWithdrawalLimit", equalTo(800.0f))
                .body("version", equalTo(1));
        patch(accountId, "upd-1", body).then().statusCode(200).body("version", equalTo(1));
    }

    @Test
    @DisplayName("answers 409 for an update from a stale version")
    void answers409ForAnUpdateFromAStaleVersion() {
        String accountId = openedAccount();
        patch(accountId, "upd-1", Map.of("alias", "Viajes", "version", 0)).then().statusCode(200);

        patch(accountId, "upd-2", Map.of("alias", "Otro", "version", 0))
                .then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @DisplayName("closes a zero-balance account with 200")
    void closesAZeroBalanceAccountWith200() {
        String accountId = openedAccount();

        close(accountId, "close-1", 0).then().statusCode(200).body("status", equalTo("CLOSED")).body("version", equalTo(1));
    }

    @Test
    @DisplayName("answers 409 when closing an account with funds")
    void answers409WhenClosingAnAccountWithFunds() {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(accountId, uniqueNumber(), Id.create(customerId),
                Money.create(new BigDecimal("10.00"), "USD")));

        close(accountId.getValue(), "close-1", 0).then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @DisplayName("a CLOSED account answers 409 to a withdrawal and to an interest credit")
    void aClosedAccountAnswers409ToAWithdrawalAndToAnInterestCredit() {
        String accountId = openedAccount();
        close(accountId, "close-1", 0).then().statusCode(200);
        Id cardId = Id.generate();
        cardRepository.save(Card.create(cardId, Id.create(customerId), "{noop}pin", 0, false, 0L));
        AtmSession session = AtmSession.open(Id.create(customerId), cardId, Instant.now());
        atmSessionRepository.save(session);

        given().header("Authorization", "Bearer " + TestAccessTokens.atm())
                .header("X-Atm-Session", session.getId().getValue())
                .header("Idempotency-Key", key())
                .contentType(ContentType.JSON).body(Map.of("amount", 10.00, "currency", "USD"))
                .post("/internal/accounts/{id}/withdrawals", accountId)
                .then().statusCode(409).contentType("application/problem+json");
        given().header("Authorization", "Bearer " + TestAccessTokens.interests())
                .header("Idempotency-Key", key())
                .contentType(ContentType.JSON)
                .body(Map.of("year", 2025, "amount", 5.00, "currency", "USD", "interestRate", 0.035,
                        "openingBalance", 0.00, "closingBalance", 5.00))
                .post("/internal/accounts/{id}/interest-credits", accountId)
                .then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @DisplayName("answers 401 without a token and 403 to a token without accounts:write")
    void answers401WithoutATokenAnd403ToATokenWithoutAccountsWrite() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body(Map.of("customerId", customerId, "currency", "USD"))
                .post("/internal/accounts").then().statusCode(401);
        given().header("Authorization", "Bearer " + TestAccessTokens.web(customerId))
                .contentType(ContentType.JSON).header("Idempotency-Key", key())
                .body(Map.of("customerId", customerId, "currency", "USD"))
                .post("/internal/accounts").then().statusCode(403);
    }

    private static RequestSpecification asAccountsAdmin() {
        return given().header("Authorization", "Bearer "
                + TestAccessTokens.token("accounts-admin", Channel.ACCOUNTS_ADMIN).subject("accounts-admin").sign());
    }

    private static Response open(String idempotencyKey, String customer) {
        return asAccountsAdmin().contentType(ContentType.JSON).header("Idempotency-Key", idempotencyKey)
                .body(Map.of("customerId", customer, "currency", "USD"))
                .post("/internal/accounts");
    }

    private static Response patch(String accountId, String idempotencyKey, Map<String, Object> body) {
        return asAccountsAdmin().contentType(ContentType.JSON).header("Idempotency-Key", idempotencyKey)
                .body(body).patch("/internal/accounts/{id}", accountId);
    }

    private static Response close(String accountId, String idempotencyKey, long version) {
        return asAccountsAdmin().contentType(ContentType.JSON).header("Idempotency-Key", idempotencyKey)
                .body(Map.of("version", version)).post("/internal/accounts/{id}/closure", accountId);
    }

    private String openedAccount() {
        return open(key(), customerId).then().statusCode(201).extract().path("id");
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    private static AccountNumber uniqueNumber() {
        return AccountNumber.create(String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L)));
    }
}
