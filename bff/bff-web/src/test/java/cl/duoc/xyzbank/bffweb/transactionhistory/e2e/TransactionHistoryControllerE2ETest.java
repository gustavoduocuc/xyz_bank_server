package cl.duoc.xyzbank.bffweb.transactionhistory.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Transaction History controller")
class TransactionHistoryControllerE2ETest {

    /*
     * Cases:
     * 1. Filtered request returns matching transactions
     * 2. Invalid filter is a validation error
     * 3. Unknown account is not found
     */

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
    @DisplayName("returns transactions filtered by date range and type")
    void returnsTransactionsFilteredByDateRangeAndType() {
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/accounts/account-1/transactions"))
                .willReturn(json(
                        "{\"items\":[{\"id\":\"tx-1\",\"type\":\"DEBIT\",\"amount\":50.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-10\",\"description\":null}],\"nextCursor\":null}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .queryParam("from", "2026-01-01")
                .queryParam("to", "2026-01-31")
                .queryParam("type", "DEBIT")
                .when()
                .get("/accounts/{accountId}/transactions", "account-1")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo("tx-1"))
                .body("items[0].type", equalTo("DEBIT"));
    }

    @Test
    @DisplayName("rejects an invalid date range")
    void rejectsAnInvalidDateRange() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .queryParam("from", "2026-02-01")
                .queryParam("to", "2026-01-01")
                .when()
                .get("/accounts/{accountId}/transactions", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("responds with not-found for an unknown account")
    void respondsWithNotFoundForAnUnknownAccount() {
        CORE_SERVICE.stubFor(get(urlPathEqualTo("/internal/accounts/unknown/transactions"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Account unknown not found\"}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .when()
                .get("/accounts/{accountId}/transactions", "unknown")
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
