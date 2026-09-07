package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Returns the balance of an existing account
     * 2. Returns 404 with a problem+json body for an unknown account
     * 3. Returns 422 with a problem+json body for a malformed account id
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
    void returnsTheBalanceOfAnExistingAccount() {
        Id id = Id.generate();
        Account account = Account.create(
                id, AccountNumber.create("1234567890"), Id.generate(),
                Money.create(new BigDecimal("300.00"), "USD"));
        accountRepository.save(account);

        given()
                .when().get("/internal/accounts/{accountId}/balance", id.getValue())
                .then()
                .statusCode(200)
                .body("accountId", equalTo(id.getValue()))
                .body("balance", equalTo(300.00f))
                .body("currency", equalTo("USD"));
    }

    @Test
    void returnsNotFoundForAnUnknownAccount() {
        given()
                .when().get("/internal/accounts/{accountId}/balance", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    void returnsUnprocessableEntityForAMalformedAccountId() {
        given()
                .when().get("/internal/accounts/{accountId}/balance", "   ")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
