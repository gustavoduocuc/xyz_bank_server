package cl.duoc.xyzbank.coreservice.interests.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The InterestCredit controller")
class InterestCreditControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Successful credit returns 201 and the new balance is reflected by a follow-up GET
     * 2. Returns 404 for an unknown account
     * 3. Returns 422 for a missing Idempotency-Key header
     * 4. Returns 422 for a non-positive amount
     * 5. Returns 422 for a currency mismatch
     * 6. Repeating an Idempotency-Key with the same body replays the original result
     * 7. Repeating an Idempotency-Key with a different amount returns 409
     * 8. Crediting the same year under a different key returns 409
     * 9. Two concurrent credits on the same account and year: exactly one succeeds
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    private Id ownerId;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        ownerId = Id.generate();
        customerRepository.save(Customer.create(ownerId, "Jane Doe", "jane.doe+" + ownerId.getValue() + "@xyzbank.cl"));
    }

    private RequestSpecification asInterestsService() {
        return given()
                .header("X-Service-Credential", "dev-service-credential-interests")
                .header("Authorization", "Bearer " + tokenAdapter.issue("interests-service", Channel.INTERESTS, null));
    }

    private RequestSpecification asOwner() {
        return given()
                .header("X-Service-Credential", "dev-service-credential-web")
                .header("Authorization", "Bearer " + tokenAdapter.issue(ownerId.getValue(), Channel.WEB, null));
    }

    @Test
    @DisplayName("returns 201 and the new balance is reflected by a follow-up GET")
    void returns201AndTheNewBalanceIsReflectedByAFollowUpGet() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-1")
                .contentType("application/json")
                .body(creditBody(2025, 35.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(1035.00f))
                .body("accountId", equalTo(accountId.getValue()))
                .body("year", equalTo(2025));

        asOwner()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(1035.00f));
    }

    @Test
    @DisplayName("returns 404 for an unknown account")
    void returnsNotFoundForAnUnknownAccount() {
        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-2")
                .contentType("application/json")
                .body(creditBody(2025, 10.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a missing Idempotency-Key header")
    void returnsUnprocessableEntityForAMissingIdempotencyKeyHeader() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .contentType("application/json")
                .body(creditBody(2025, 10.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a non-positive amount")
    void returnsUnprocessableEntityForANonPositiveAmount() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-3")
                .contentType("application/json")
                .body(creditBody(2025, 0.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a currency mismatch")
    void returnsUnprocessableEntityForACurrencyMismatch() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-4")
                .contentType("application/json")
                .body(Map.of(
                        "year", 2025,
                        "amount", 35.00,
                        "currency", "CLP",
                        "interestRate", 0.035,
                        "openingBalance", 1000.00,
                        "closingBalance", 1035.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("repeating an Idempotency-Key with the same body replays the original result")
    void repeatingAnIdempotencyKeyWithTheSameBodyReplaysTheOriginalResult() {
        Id accountId = anExistingAccount("1000.00");
        Map<String, Object> body = creditBody(2025, 35.00);

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-5")
                .contentType("application/json")
                .body(body)
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201);

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-5")
                .contentType("application/json")
                .body(body)
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(1035.00f));

        asOwner()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(1035.00f));
    }

    @Test
    @DisplayName("repeating an Idempotency-Key with a different amount returns 409, leaving the balance unchanged")
    void repeatingAnIdempotencyKeyWithADifferentAmountReturns409() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-6")
                .contentType("application/json")
                .body(creditBody(2025, 35.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201);

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-6")
                .contentType("application/json")
                .body(creditBody(2025, 20.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(409)
                .contentType("application/problem+json");

        asOwner()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(1035.00f));
    }

    @Test
    @DisplayName("crediting the same year under a different key returns 409")
    void creditingTheSameYearUnderADifferentKeyReturns409() {
        Id accountId = anExistingAccount("1000.00");

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-7a")
                .contentType("application/json")
                .body(creditBody(2025, 35.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(201);

        asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-7b")
                .contentType("application/json")
                .body(creditBody(2025, 35.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .then()
                .statusCode(409)
                .contentType("application/problem+json");

        asOwner()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(1035.00f));
    }

    @Test
    @DisplayName("exactly one of two concurrent credits on the same account and year succeeds")
    void exactlyOneOfTwoConcurrentCreditsOnTheSameAccountAndYearSucceeds() {
        Id accountId = anExistingAccount("1000.00");

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-8a")
                .contentType("application/json")
                .body(creditBody(2025, 35.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .statusCode());
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> asInterestsService()
                .header("Idempotency-Key", "interest-e2e-key-8b")
                .contentType("application/json")
                .body(creditBody(2025, 40.00))
                .when().post("/internal/accounts/{accountId}/interest-credits", accountId.getValue())
                .statusCode());

        int firstStatus = first.join();
        int secondStatus = second.join();

        long successCount = java.util.stream.Stream.of(firstStatus, secondStatus).filter(s -> s == 201).count();
        long conflictCount = java.util.stream.Stream.of(firstStatus, secondStatus).filter(s -> s == 409).count();
        assertEquals(1, successCount);
        assertEquals(1, conflictCount);
    }

    private Map<String, Object> creditBody(int year, double amount) {
        return Map.of(
                "year", year,
                "amount", amount,
                "currency", "USD",
                "interestRate", 0.035,
                "openingBalance", 1000.00,
                "closingBalance", 1000.00 + amount);
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create(randomAccountNumber()), ownerId,
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }
}
