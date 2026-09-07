package cl.duoc.xyzbank.coreservice.transactions.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Transaction controller")
class TransactionControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Walks the full pagination sequence (first page -> follow cursor -> last page)
     * 2. Filters by date range and type
     * 3. Returns an empty page for an account with no transactions
     * 4. Returns 404 for an unknown account
     * 5. Returns 422 for an inverted date range
     * 6. Returns 422 for an unrecognized type
     * 7. Returns 422 for a non-positive page size
     * 8. Returns 422 for a malformed cursor
     * 9. Clamps an oversized page size instead of rejecting it
     * 10. Returns the detail of an existing transaction
     * 11. Returns 404 for an unknown transaction
     * 12. Returns 422 for a malformed transaction id
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("walks the full pagination sequence")
    void walksTheFullPaginationSequence() {
        Id accountId = anExistingAccount();
        for (int day = 1; day <= 5; day++) {
            saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, day));
        }

        Response firstPage = given()
                .queryParam("pageSize", 3)
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(200)
                .body("items", hasSize(3))
                .extract().response();
        String nextCursor = firstPage.jsonPath().getString("nextCursor");
        org.junit.jupiter.api.Assertions.assertTrue(nextCursor != null && !nextCursor.isBlank());

        given()
                .queryParam("pageSize", 3)
                .queryParam("cursor", nextCursor)
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("nextCursor", nullValue());
    }

    @Test
    @DisplayName("filters by date range and type")
    void filtersByDateRangeAndType() {
        Id accountId = anExistingAccount();
        saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, 1));
        saveTransaction(accountId, TransactionType.CREDIT, LocalDate.of(2026, 1, 20));

        given()
                .queryParam("from", "2026-01-10")
                .queryParam("to", "2026-01-31")
                .queryParam("type", "CREDIT")
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].type", equalTo("CREDIT"));
    }

    @Test
    @DisplayName("returns an empty page for an account with no transactions")
    void returnsAnEmptyPageForAnAccountWithNoTransactions() {
        Id accountId = anExistingAccount();

        given()
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(200)
                .body("items", empty())
                .body("nextCursor", nullValue());
    }

    @Test
    @DisplayName("returns 404 for an unknown account")
    void returnsNotFoundForAnUnknownAccount() {
        given()
                .when().get("/internal/accounts/{accountId}/transactions", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for an inverted date range")
    void returnsUnprocessableEntityForAnInvertedDateRange() {
        Id accountId = anExistingAccount();

        given()
                .queryParam("from", "2026-02-01")
                .queryParam("to", "2026-01-01")
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for an unrecognized type")
    void returnsUnprocessableEntityForAnUnrecognizedType() {
        Id accountId = anExistingAccount();

        given()
                .queryParam("type", "REFUND")
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a non-positive page size")
    void returnsUnprocessableEntityForANonPositivePageSize() {
        Id accountId = anExistingAccount();

        given()
                .queryParam("pageSize", 0)
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a malformed cursor")
    void returnsUnprocessableEntityForAMalformedCursor() {
        Id accountId = anExistingAccount();

        given()
                .queryParam("cursor", "not-a-real-cursor")
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("clamps an oversized page size instead of rejecting it")
    void clampsAnOversizedPageSizeInsteadOfRejectingIt() {
        Id accountId = anExistingAccount();
        for (int day = 1; day <= 5; day++) {
            saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, day));
        }

        given()
                .queryParam("pageSize", 1000)
                .when().get("/internal/accounts/{accountId}/transactions", accountId.getValue())
                .then()
                .statusCode(200)
                .body("items", hasSize(5));
    }

    @Test
    @DisplayName("returns the detail of an existing transaction")
    void returnsTheDetailOfAnExistingTransaction() {
        Id accountId = anExistingAccount();
        Id transactionId = Id.generate();
        transactionRepository.save(Transaction.create(
                transactionId, accountId, TransactionType.DEBIT,
                Money.create(new BigDecimal("42.00"), "USD"), LocalDate.of(2026, 1, 5), "Groceries"));

        given()
                .when().get("/internal/transactions/{transactionId}", transactionId.getValue())
                .then()
                .statusCode(200)
                .body("id", equalTo(transactionId.getValue()))
                .body("accountId", equalTo(accountId.getValue()))
                .body("type", equalTo("DEBIT"))
                .body("description", equalTo("Groceries"));
    }

    @Test
    @DisplayName("returns 404 for an unknown transaction")
    void returnsNotFoundForAnUnknownTransaction() {
        given()
                .when().get("/internal/transactions/{transactionId}", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a malformed transaction id")
    void returnsUnprocessableEntityForAMalformedTransactionId() {
        given()
                .when().get("/internal/transactions/{transactionId}", "   ")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    private Id anExistingAccount() {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create(randomAccountNumber()), Id.generate(),
                Money.create(new BigDecimal("100.00"), "USD")));
        return accountId;
    }

    private void saveTransaction(Id accountId, TransactionType type, LocalDate occurredOn) {
        transactionRepository.save(Transaction.create(
                Id.generate(), accountId, type, Money.create(new BigDecimal("10.00"), "USD"), occurredOn, null));
    }

    private String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }
}
