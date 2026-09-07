package cl.duoc.xyzbank.coreservice.withdrawals.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
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
@DisplayName("The Withdrawal controller")
class WithdrawalControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Successful withdrawal returns 201 and the new balance is reflected by a follow-up GET
     * 2. Returns 404 for an unknown account
     * 3. Returns 422 for a missing Idempotency-Key header
     * 4. Returns 422 for a non-positive amount
     * 5. Returns 422 for a currency mismatch
     * 6. Returns 422 for insufficient funds, and the balance is unchanged afterward
     * 7. Returns 422 for an exceeded daily limit, and the balance is unchanged afterward
     * 8. Repeating an Idempotency-Key with the same body replays the original result
     * 9. Repeating an Idempotency-Key with a different amount returns 409
     * 10. Two concurrent withdrawals on the same account: exactly one succeeds, the other gets 409
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("returns 201 and the new balance is reflected by a follow-up GET")
    void returns201AndTheNewBalanceIsReflectedByAFollowUpGet() {
        Id accountId = anExistingAccount("500.00");

        given()
                .header("Idempotency-Key", "e2e-key-1")
                .contentType("application/json")
                .body(Map.of("amount", 100.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(400.00f))
                .body("accountId", equalTo(accountId.getValue()));

        given()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(400.00f));
    }

    @Test
    @DisplayName("returns 404 for an unknown account")
    void returnsNotFoundForAnUnknownAccount() {
        given()
                .header("Idempotency-Key", "e2e-key-2")
                .contentType("application/json")
                .body(Map.of("amount", 10.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a missing Idempotency-Key header")
    void returnsUnprocessableEntityForAMissingIdempotencyKeyHeader() {
        Id accountId = anExistingAccount("500.00");

        given()
                .contentType("application/json")
                .body(Map.of("amount", 10.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a non-positive amount")
    void returnsUnprocessableEntityForANonPositiveAmount() {
        Id accountId = anExistingAccount("500.00");

        given()
                .header("Idempotency-Key", "e2e-key-3")
                .contentType("application/json")
                .body(Map.of("amount", 0.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a currency mismatch")
    void returnsUnprocessableEntityForACurrencyMismatch() {
        Id accountId = anExistingAccount("500.00");

        given()
                .header("Idempotency-Key", "e2e-key-4")
                .contentType("application/json")
                .body(Map.of("amount", 10.00, "currency", "CLP"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for insufficient funds, and the balance is unchanged afterward")
    void returnsUnprocessableEntityForInsufficientFundsAndTheBalanceIsUnchangedAfterward() {
        Id accountId = anExistingAccount("50.00");

        given()
                .header("Idempotency-Key", "e2e-key-5")
                .contentType("application/json")
                .body(Map.of("amount", 100.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");

        given()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(50.00f));
    }

    @Test
    @DisplayName("returns 422 for an exceeded daily limit, and the balance is unchanged afterward")
    void returnsUnprocessableEntityForAnExceededDailyLimitAndTheBalanceIsUnchangedAfterward() {
        // Balance stays well above the withdrawal amount so this exercises the daily-limit
        // check specifically, not insufficient funds (the default app.withdrawals.daily-limit is 5000.00).
        Id accountId = anExistingAccount("50000.00");

        given()
                .header("Idempotency-Key", "e2e-key-6")
                .contentType("application/json")
                .body(Map.of("amount", 6000.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");

        given()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(50000.00f));
    }

    @Test
    @DisplayName("repeating an Idempotency-Key with the same body replays the original result")
    void repeatingAnIdempotencyKeyWithTheSameBodyReplaysTheOriginalResult() {
        Id accountId = anExistingAccount("500.00");
        Map<String, Object> body = Map.of("amount", 100.00, "currency", "USD");

        given()
                .header("Idempotency-Key", "e2e-key-7")
                .contentType("application/json")
                .body(body)
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(201);

        given()
                .header("Idempotency-Key", "e2e-key-7")
                .contentType("application/json")
                .body(body)
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(201)
                .body("newBalance", equalTo(400.00f));

        given()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(400.00f));
    }

    @Test
    @DisplayName("repeating an Idempotency-Key with a different amount returns 409")
    void repeatingAnIdempotencyKeyWithADifferentAmountReturns409() {
        Id accountId = anExistingAccount("500.00");

        given()
                .header("Idempotency-Key", "e2e-key-8")
                .contentType("application/json")
                .body(Map.of("amount", 100.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(201);

        given()
                .header("Idempotency-Key", "e2e-key-8")
                .contentType("application/json")
                .body(Map.of("amount", 50.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .then()
                .statusCode(409)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("exactly one of two concurrent withdrawals on the same account succeeds")
    void exactlyOneOfTwoConcurrentWithdrawalsOnTheSameAccountSucceeds() {
        Id accountId = anExistingAccount("100.00");

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> given()
                .header("Idempotency-Key", "e2e-key-9a")
                .contentType("application/json")
                .body(Map.of("amount", 60.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .statusCode());
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> given()
                .header("Idempotency-Key", "e2e-key-9b")
                .contentType("application/json")
                .body(Map.of("amount", 60.00, "currency", "USD"))
                .when().post("/internal/accounts/{accountId}/withdrawals", accountId.getValue())
                .statusCode());

        int firstStatus = first.join();
        int secondStatus = second.join();

        long successCount = java.util.stream.Stream.of(firstStatus, secondStatus).filter(s -> s == 201).count();
        long conflictCount = java.util.stream.Stream.of(firstStatus, secondStatus).filter(s -> s == 409).count();
        assertEquals(1, successCount);
        assertEquals(1, conflictCount);

        given()
                .when().get("/internal/accounts/{accountId}/balance", accountId.getValue())
                .then()
                .statusCode(200)
                .body("balance", equalTo(40.00f));
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create(randomAccountNumber()), Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }
}
