package cl.duoc.xyzbank.bffweb.interestview.e2e;

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
@DisplayName("The Interest View controller")
class InterestViewControllerE2ETest {

    /*
     * Cases:
     * 1. Successful interest view
     * 2. Missing year is a validation error
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
    @DisplayName("returns the annual interest summary")
    void returnsTheAnnualInterestSummary() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/interest-summary?year=2026"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"accountId\":\"account-1\",\"year\":2026,\"openingBalance\":1000.00,\"closingBalance\":1100.00,\"interestRate\":0.05,\"interestAmount\":50.00,\"currency\":\"USD\"}")));

        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .queryParam("year", "2026")
                .when()
                .get("/accounts/{accountId}/interest-summary", "account-1")
                .then()
                .statusCode(200)
                .body("accountId", equalTo("account-1"))
                .body("year", equalTo(2026))
                .body("openingBalance", equalTo(1000.00f))
                .body("interestAmount", equalTo(50.00f));
    }

    @Test
    @DisplayName("rejects a missing year query parameter")
    void rejectsAMissingYearQueryParameter() {
        given()
                .header("X-Customer-Id", "customer-1")
                .header("X-Channel", "web")
                .when()
                .get("/accounts/{accountId}/interest-summary", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
