package cl.duoc.xyzbank.paymentsservice.payments.e2e;

import cl.duoc.xyzbank.paymentsservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.paymentsservice.testsupport.TestCoreService;
import cl.duoc.xyzbank.paymentsservice.testsupport.TestTokens;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The Payment controller")
class PaymentControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. A transfer returns 201 COMPLETED; the same key again returns the same id with one posting call
     * 2. A transfer core-service refuses (422) returns 201 REJECTED
     * 3. A deposit and a bill payment return 201 COMPLETED with their types
     * 4. core-service down returns 503 and the payment stays PENDING
     * 5. GET returns 200 with the payment, and 404 for an unknown id
     * 6. A POST without an Idempotency-Key returns 400 and records nothing
     * 7. No token returns 401, and a payments:read token on a POST returns 403
     */

    private static final String POSTINGS = "/internal/postings";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String source = UUID.randomUUID().toString();
    private final String destination = UUID.randomUUID().toString();

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("completes a transfer once, even when its key is repeated")
    void completesATransferOnceEvenWhenItsKeyIsRepeated() {
        coreAnswers(201);
        String key = "t-" + UUID.randomUUID();

        Response first = pay("/internal/transfers", key, transfer());
        Response second = pay("/internal/transfers", key, transfer());

        assertEquals(201, first.statusCode());
        assertEquals("COMPLETED", first.path("status"));
        assertEquals("TRANSFER", first.path("type"));
        assertEquals(201, second.statusCode());
        assertEquals((String) first.path("id"), second.path("id"));
        TestCoreService.server().verify(1, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    @Test
    @DisplayName("rejects a transfer core-service refuses")
    void rejectsATransferCoreServiceRefuses() {
        coreAnswers(422);

        Response response = pay("/internal/transfers", "t-" + UUID.randomUUID(), transfer());

        assertEquals(201, response.statusCode());
        assertEquals("REJECTED", response.path("status"));
    }

    @Test
    @DisplayName("completes a deposit and a bill payment")
    void completesADepositAndABillPayment() {
        coreAnswers(201);

        Response deposit = pay("/internal/deposits", "d-" + UUID.randomUUID(),
                Map.of("destinationAccountId", destination, "amount", 200.00, "currency", "USD"));
        Response billPayment = pay("/internal/bill-payments", "b-" + UUID.randomUUID(),
                Map.of("sourceAccountId", source, "amount", 30.00, "currency", "USD"));

        assertEquals(201, deposit.statusCode());
        assertEquals("DEPOSIT", deposit.path("type"));
        assertEquals("COMPLETED", deposit.path("status"));
        assertEquals(201, billPayment.statusCode());
        assertEquals("BILL_PAYMENT", billPayment.path("type"));
        assertEquals("COMPLETED", billPayment.path("status"));
    }

    @Test
    @DisplayName("returns 503 and leaves the payment PENDING when core-service is down")
    void returns503AndLeavesThePaymentPendingWhenCoreServiceIsDown() {
        coreAnswers(503);
        String key = "t-" + UUID.randomUUID();

        Response response = pay("/internal/transfers", key, transfer());

        assertEquals(503, response.statusCode());
        assertEquals("application/problem+json", response.contentType());
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM payments.payments WHERE idempotency_key = ?", String.class, key));
    }

    @Test
    @DisplayName("returns a payment by id, and 404 for an unknown id")
    void returnsAPaymentByIdAnd404ForAnUnknownId() {
        coreAnswers(201);
        String id = pay("/internal/transfers", "t-" + UUID.randomUUID(), transfer()).path("id");

        Response found = given().header("Authorization", "Bearer " + TestTokens.paymentsReader())
                .when().get("/internal/payments/{id}", id);
        Response unknown = given().header("Authorization", "Bearer " + TestTokens.paymentsReader())
                .when().get("/internal/payments/{id}", UUID.randomUUID());

        assertEquals(200, found.statusCode());
        assertEquals("COMPLETED", found.path("status"));
        assertEquals(source, found.path("sourceAccountId"));
        assertEquals(404, unknown.statusCode());
    }

    @Test
    @DisplayName("returns 400 for a POST without an Idempotency-Key and records nothing")
    void returns400ForAPostWithoutAnIdempotencyKey() {
        Response response = given().header("Authorization", "Bearer " + TestTokens.paymentsAdmin())
                .contentType("application/json").body(transfer())
                .when().post("/internal/transfers");

        assertEquals(400, response.statusCode());
        TestCoreService.server().verify(0, postRequestedFor(urlEqualTo(POSTINGS)));
    }

    @Test
    @DisplayName("returns 401 without a token and 403 for a read-only token on a POST")
    void returns401WithoutATokenAnd403ForAReadOnlyTokenOnAPost() {
        String key = "t-" + UUID.randomUUID();

        Response anonymous = given().header("Idempotency-Key", key).contentType("application/json")
                .body(transfer()).when().post("/internal/transfers");
        Response readOnly = given().header("Authorization", "Bearer " + TestTokens.paymentsReader())
                .header("Idempotency-Key", key).contentType("application/json")
                .body(transfer()).when().post("/internal/transfers");

        assertEquals(401, anonymous.statusCode());
        assertEquals(403, readOnly.statusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments.payments WHERE idempotency_key = ?", Integer.class, key));
    }

    private Map<String, Object> transfer() {
        return Map.of("sourceAccountId", source, "destinationAccountId", destination,
                "amount", 100.00, "currency", "USD");
    }

    private static Response pay(String path, String key, Map<String, Object> body) {
        return given()
                .header("Authorization", "Bearer " + TestTokens.paymentsAdmin())
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(body)
                .when().post(path);
    }

    private static void coreAnswers(int status) {
        TestCoreService.server().stubFor(post(urlEqualTo(POSTINGS)).willReturn(aResponse().withStatus(status)));
    }
}
