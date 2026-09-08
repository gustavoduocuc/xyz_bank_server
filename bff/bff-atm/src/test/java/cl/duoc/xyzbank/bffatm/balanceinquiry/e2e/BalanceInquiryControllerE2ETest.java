package cl.duoc.xyzbank.bffatm.balanceinquiry.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Balance Inquiry controller")
class BalanceInquiryControllerE2ETest {

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
    @DisplayName("returns the account balance")
    void returnsTheAccountBalance() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"account-1\",\"balance\":250.00,\"currency\":\"USD\"}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .header("X-Terminal-Id", "terminal-1")
                .when()
                .get("/accounts/{accountId}/balance", "account-1")
                .then()
                .statusCode(200)
                .body("balance", equalTo(250.00f))
                .body("currency", equalTo("USD"));
    }

    @Test
    @DisplayName("rejects a caller whose channel is not atm")
    void rejectsACallerWhoseChannelIsNotAtm() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .when()
                .get("/accounts/{accountId}/balance", "account-1")
                .then()
                .statusCode(403)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("rejects a missing terminal id")
    void rejectsAMissingTerminalId() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "atm")
                .when()
                .get("/accounts/{accountId}/balance", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
