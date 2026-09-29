package cl.duoc.xyzbank.coreservice.interests.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
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
@DisplayName("The Interest controller")
class InterestControllerE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Returns an existing summary
     * 2. Returns 404 when no summary exists for that account and year
     * 3. Returns 422 for a missing year
     * 4. Returns 422 for a non-numeric year
     * 5. Returns 422 for a malformed account id
     */

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InterestSummaryRepository interestSummaryRepository;

    private Id ownerId;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        ownerId = Id.generate();
        customerRepository.save(Customer.create(ownerId, "Jane Doe", "jane.doe+" + ownerId.getValue() + "@xyzbank.cl"));
    }

    private RequestSpecification asOwner() {
        return given()
                .header("Authorization", "Bearer " + TestAccessTokens.web(ownerId.getValue()));
    }

    private Id anExistingAccount() {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create(randomAccountNumber()), ownerId,
                Money.create(new BigDecimal("100.00"), "USD")));
        return accountId;
    }

    private String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }

    @Test
    @DisplayName("returns an existing summary")
    void returnsAnExistingSummary() {
        Id accountId = anExistingAccount();
        interestSummaryRepository.save(AnnualInterestSummary.create(
                Id.generate(), accountId, 2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1025.00"), "USD"),
                new BigDecimal("2.5000"),
                Money.create(new BigDecimal("25.00"), "USD")));

        asOwner()
                .queryParam("year", 2025)
                .when().get("/internal/accounts/{accountId}/interest-summary", accountId.getValue())
                .then()
                .statusCode(200)
                .body("accountId", equalTo(accountId.getValue()))
                .body("year", equalTo(2025))
                .body("currency", equalTo("USD"));
    }

    @Test
    @DisplayName("returns 404 when no summary exists for that account and year")
    void returnsNotFoundWhenNoSummaryExistsForThatAccountAndYear() {
        Id accountId = anExistingAccount();

        asOwner()
                .queryParam("year", 2025)
                .when().get("/internal/accounts/{accountId}/interest-summary", accountId.getValue())
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a missing year")
    void returnsUnprocessableEntityForAMissingYear() {
        Id accountId = anExistingAccount();

        asOwner()
                .when().get("/internal/accounts/{accountId}/interest-summary", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a non-numeric year")
    void returnsUnprocessableEntityForANonNumericYear() {
        Id accountId = anExistingAccount();

        asOwner()
                .queryParam("year", "abcd")
                .when().get("/internal/accounts/{accountId}/interest-summary", accountId.getValue())
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("returns 422 for a malformed account id")
    void returnsUnprocessableEntityForAMalformedAccountId() {
        asOwner()
                .queryParam("year", 2025)
                .when().get("/internal/accounts/{accountId}/interest-summary", "   ")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
