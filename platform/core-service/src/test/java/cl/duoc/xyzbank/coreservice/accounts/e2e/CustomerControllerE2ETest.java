package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Returns the profile of an existing customer
     * 2. Returns 404 with a problem+json body for an unknown customer
     * 3. Returns 422 with a problem+json body for a malformed customer id
     * 4. Returns every account owned by a customer with multiple accounts
     * 5. Returns an empty list for a customer with no accounts
     * 6. Returns 404 for the accounts of an unknown customer
     */

    @LocalServerPort
    private int port;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    void returnsTheProfileOfAnExistingCustomer() {
        Id id = Id.generate();
        customerRepository.save(Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl"));

        given()
                .when().get("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(id.getValue()))
                .body("fullName", org.hamcrest.Matchers.equalTo("Jane Doe"))
                .body("email", org.hamcrest.Matchers.equalTo("jane.doe@xyzbank.cl"));
    }

    @Test
    void returnsNotFoundForAnUnknownCustomer() {
        given()
                .when().get("/internal/customers/{customerId}", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    void returnsUnprocessableEntityForAMalformedCustomerId() {
        given()
                .when().get("/internal/customers/{customerId}", "   ")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    void returnsEveryAccountOwnedByACustomerWithMultipleAccounts() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe@xyzbank.cl"));
        accountRepository.save(anAccountFor(customerId, "1111111111"));
        accountRepository.save(anAccountFor(customerId, "2222222222"));

        given()
                .when().get("/internal/customers/{customerId}/accounts", customerId.getValue())
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void returnsAnEmptyListForACustomerWithNoAccounts() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe@xyzbank.cl"));

        given()
                .when().get("/internal/customers/{customerId}/accounts", customerId.getValue())
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    void returnsNotFoundForTheAccountsOfAnUnknownCustomer() {
        given()
                .when().get("/internal/customers/{customerId}/accounts", Id.generate().getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    private Account anAccountFor(Id customerId, String accountNumber) {
        return Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("100.00"), "USD"));
    }
}
