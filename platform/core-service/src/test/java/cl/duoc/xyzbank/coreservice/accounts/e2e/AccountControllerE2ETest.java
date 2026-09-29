package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Account controller")
class AccountControllerE2ETest extends AbstractCoreServiceIT {

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

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    private RequestSpecification asOwner(Id customerId) {
        return given()
                .header("Authorization", "Bearer " + TestAccessTokens.web(customerId.getValue()));
    }

    @Test
    @DisplayName("returns the balance of an existing account")
    void returnsTheBalanceOfAnExistingAccount() {
        Id id = Id.generate();
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe+" + customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                id, AccountNumber.create("1234567890"), customerId,
                Money.create(new BigDecimal("300.00"), "USD"));
        accountRepository.save(account);

        asOwner(customerId)
                .when().get("/internal/accounts/{accountId}/balance", id.getValue())
                .then()
                .statusCode(200)
                .body("accountId", equalTo(id.getValue()))
                .body("balance", equalTo(300.00f))
                .body("currency", equalTo("USD"));
    }

    @Test
    @DisplayName("returns 404 with a problem+json body for an unknown account")
    void returnsNotFoundForAnUnknownAccount() {
        asOwner(Id.generate())
                .when().get("/internal/accounts/{accountId}/balance", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 with a problem+json body for a malformed account id")
    void returnsUnprocessableEntityForAMalformedAccountId() {
        asOwner(Id.generate())
                .when().get("/internal/accounts/{accountId}/balance", "   ")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
