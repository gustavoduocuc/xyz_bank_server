package cl.duoc.xyzbank.bffweb.dashboard.e2e;

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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Dashboard controller")
class DashboardControllerE2ETest {

    /*
     * Cases:
     * 1. Successful dashboard aggregate
     * 2. Unknown customer is not found
     * 3. Non-web channel is rejected
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
    @DisplayName("aggregates profile, accounts, and latest transactions")
    void aggregatesProfileAccountsAndLatestTransactions() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1"))
                .willReturn(json("{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1/accounts"))
                .willReturn(json(
                        "[{\"id\":\"account-1\",\"accountNumber\":\"1000000001\",\"balance\":500.00,\"currency\":\"USD\"}]")));
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .willReturn(json(
                        "{\"items\":[{\"id\":\"tx-1\",\"type\":\"DEBIT\",\"amount\":50.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"description\":null}],\"nextCursor\":null}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(200)
                .body("profile.id", equalTo("customer-1"))
                .body("profile.fullName", equalTo("Ana Perez"))
                .body("accounts", hasSize(1))
                .body("accounts[0].id", equalTo("account-1"))
                .body("accounts[0].transactions", hasSize(1))
                .body("accounts[0].transactions[0].id", equalTo("tx-1"));
    }

    @Test
    @DisplayName("responds with not-found for an unknown customer")
    void respondsWithNotFoundForAnUnknownCustomer() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/unknown"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Customer unknown not found\"}")));

        given()
                .header("X-Customer-Id", "unknown")
                .header("X-Channel", "web")
                .when()
                .get("/customers/{customerId}/dashboard", "unknown")
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("rejects a caller whose channel is not web")
    void rejectsACallerWhoseChannelIsNotWeb() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "mobile")
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(403)
                .contentType("application/problem+json");
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
