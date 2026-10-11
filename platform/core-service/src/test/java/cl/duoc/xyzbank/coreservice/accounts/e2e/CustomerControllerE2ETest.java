package cl.duoc.xyzbank.coreservice.accounts.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Customer controller")
class CustomerControllerE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. No longer serves the customer profile (it moved to customers-service)
     * 2. Returns every account owned by a customer with multiple accounts
     * 3. Returns an empty list for a customer with no accounts
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    private RequestSpecification asOwner(Id customerId) {
        return given()
                .header("Authorization", "Bearer " + TestAccessTokens.web(customerId.getValue()));
    }

    @Test
    @DisplayName("no longer serves the customer profile")
    void noLongerServesTheCustomerProfile() {
        Id id = Id.generate();
        asOwner(id)
                .when().get("/internal/customers/{customerId}", id.getValue())
                .then()
                .statusCode(not(equalTo(200)));
    }

    @Test
    @DisplayName("returns every account owned by a customer with multiple accounts")
    void returnsEveryAccountOwnedByACustomerWithMultipleAccounts() {
        Id customerId = Id.generate();
        accountRepository.save(anAccountFor(customerId, "1111111111"));
        accountRepository.save(anAccountFor(customerId, "2222222222"));

        asOwner(customerId)
                .when().get("/internal/customers/{customerId}/accounts", customerId.getValue())
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    @DisplayName("returns an empty list for a customer with no accounts")
    void returnsAnEmptyListForACustomerWithNoAccounts() {
        Id customerId = Id.generate();

        asOwner(customerId)
                .when().get("/internal/customers/{customerId}/accounts", customerId.getValue())
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    private Account anAccountFor(Id customerId, String accountNumber) {
        return Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("100.00"), "USD"));
    }
}
