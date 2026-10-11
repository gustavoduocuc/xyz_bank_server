package cl.duoc.xyzbank.coreservice.postings.e2e;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Posting controller")
class PostingControllerE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. A transfer returns 201 and moves both balances; repeating the paymentId returns 201 with the
     *    same transaction ids and moves each balance once (its outbox records: TransactionConfirmedKafkaIT)
     * 2. Insufficient funds returns 422 and changes nothing
     * 3. A CLOSED account returns 409 with no partial movement
     * 4. A web token returns 403
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
    @DisplayName("applies a transfer once, even when the paymentId is repeated")
    void appliesATransferOnceEvenWhenThePaymentIdIsRepeated() {
        Id source = anAccount("500.00");
        Id destination = anAccount("0.00");
        Map<String, Object> transfer = transfer(UUID.randomUUID().toString(), source, destination, 100.00);

        Response first = post(transfer, paymentsToken());
        Response second = post(transfer, paymentsToken());

        assertEquals(201, first.statusCode());
        assertEquals(201, second.statusCode());
        List<String> firstIds = first.jsonPath().getList("entries.transactionId");
        assertEquals(2, firstIds.size());
        assertEquals(firstIds, second.jsonPath().getList("entries.transactionId"));
        assertEquals(new BigDecimal("400.00"), balanceOf(source));
        assertEquals(new BigDecimal("100.00"), balanceOf(destination));
    }

    @Test
    @DisplayName("returns 422 for insufficient funds and changes nothing")
    void returns422ForInsufficientFundsAndChangesNothing() {
        Id source = anAccount("50.00");
        Id destination = anAccount("0.00");

        Response response = post(transfer(UUID.randomUUID().toString(), source, destination, 100.00), paymentsToken());

        assertEquals(422, response.statusCode());
        assertEquals("application/problem+json", response.contentType());
        assertEquals(new BigDecimal("50.00"), balanceOf(source));
        assertEquals(new BigDecimal("0.00"), balanceOf(destination));
    }

    @Test
    @DisplayName("returns 409 for a CLOSED account with no partial movement")
    void returns409ForAClosedAccountWithNoPartialMovement() {
        Id source = anAccount("500.00");
        Id closed = Id.generate();
        Account account = Account.open(closed, AccountNumber.create(randomAccountNumber()), Id.generate(), "USD", null);
        account.close(0, "close-" + closed.getValue());
        accountRepository.save(account);

        Response response = post(transfer(UUID.randomUUID().toString(), source, closed, 100.00), paymentsToken());

        assertEquals(409, response.statusCode());
        assertEquals(new BigDecimal("500.00"), balanceOf(source));
    }

    @Test
    @DisplayName("returns 403 for a web token")
    void returns403ForAWebToken() {
        Id source = anAccount("500.00");
        Id destination = anAccount("0.00");

        Response response = post(
                transfer(UUID.randomUUID().toString(), source, destination, 100.00), TestAccessTokens.web("customer-1"));

        assertEquals(403, response.statusCode());
        assertEquals(new BigDecimal("500.00"), balanceOf(source));
    }

    private static Response post(Map<String, Object> body, String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(body)
                .when().post("/internal/postings");
    }

    private static Map<String, Object> transfer(String paymentId, Id source, Id destination, double amount) {
        return Map.of("paymentId", paymentId, "entries", List.of(
                Map.of("accountId", source.getValue(), "direction", "DEBIT", "amount", amount, "currency", "USD"),
                Map.of("accountId", destination.getValue(), "direction", "CREDIT", "amount", amount, "currency", "USD")));
    }

    private static String paymentsToken() {
        return TestAccessTokens.token("payments-service", Channel.PAYMENTS).subject("payments-service").sign();
    }

    private Id anAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(accountId, AccountNumber.create(randomAccountNumber()), Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private BigDecimal balanceOf(Id accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance().getAmount();
    }

    private static String randomAccountNumber() {
        return String.valueOf(1000000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000000L));
    }
}
