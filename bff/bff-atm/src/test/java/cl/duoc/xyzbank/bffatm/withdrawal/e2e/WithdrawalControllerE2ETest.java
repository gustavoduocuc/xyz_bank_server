package cl.duoc.xyzbank.bffatm.withdrawal.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Withdrawal controller")
class WithdrawalControllerE2ETest {

    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        CORE_SERVICE.resetAll();
    }

    @AfterAll
    static void stopCoreServiceStub() {
        CORE_SERVICE.stop();
    }

    @Test
    @DisplayName("returns a successful withdrawal")
    void returnsASuccessfulWithdrawal() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .header("X-Terminal-Id", "terminal-1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201)
                .body("transactionId", equalTo("tx-1"))
                .body("newBalance", equalTo(210.00f));
    }

    @Test
    @DisplayName("rejects a missing idempotency key")
    void rejectsAMissingIdempotencyKey() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .header("X-Terminal-Id", "terminal-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("relays a core-service conflict as 409")
    void relaysACoreServiceConflictAs409() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Conflict\"}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .header("X-Terminal-Id", "terminal-1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(409)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("propagates a generated correlation id when the inbound header is absent")
    void propagatesAGeneratedCorrelationIdWhenTheInboundHeaderIsAbsent() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));

        String correlationId = given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .header("X-Terminal-Id", "terminal-1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201)
                .header("X-Correlation-Id", not(emptyOrNullString()))
                .extract()
                .header("X-Correlation-Id");

        CORE_SERVICE.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withHeader("X-Correlation-Id", WireMock.equalTo(correlationId)));
    }
}
