package cl.duoc.xyzbank.interestsservice.interestview.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Interest Controller E2E")
class InterestControllerE2ETest {

    /*
     * Cases:
     * 1. Returns interest summary from core-service
     * 2. Returns 404 when core-service returns 404
     * 3. Returns 422 for invalid year parameter
     * 4. Returns 503 when core-service is unavailable
     */

    private static WireMockServer coreServiceMock;

    @LocalServerPort
    private int port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startWireMock() {
        coreServiceMock = new WireMockServer(0);
        coreServiceMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        coreServiceMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", () -> coreServiceMock.baseUrl());
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        coreServiceMock.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.reset());
    }

    @Nested
    @DisplayName("GET /accounts/{accountId}/interest-summary")
    class GetInterestSummary {

        @Test
        @DisplayName("returns interest summary from core-service")
        void returnsInterestSummaryFromCoreService() {
            coreServiceMock.stubFor(get(urlPathEqualTo("/internal/accounts/account-123/interest-summary"))
                    .withQueryParam("year", com.github.tomakehurst.wiremock.client.WireMock.equalTo("2025"))
                    .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                            "Bearer user-jwt"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "accountId": "account-123",
                                    "year": 2025,
                                    "openingBalance": 1000.00,
                                    "closingBalance": 1035.00,
                                    "interestRate": 0.035,
                                    "interestAmount": 35.00,
                                    "currency": "USD"
                                }
                                """)));

            given()
                    .header("Authorization", "Bearer user-jwt")
                    .queryParam("year", "2025")
                    .when()
                    .get("/accounts/{accountId}/interest-summary", "account-123")
                    .then()
                    .statusCode(200)
                    .body("accountId", equalTo("account-123"))
                    .body("year", equalTo(2025))
                    .body("openingBalance", equalTo(1000.00f))
                    .body("closingBalance", equalTo(1035.00f))
                    .body("interestRate", equalTo(0.035f))
                    .body("interestAmount", equalTo(35.00f))
                    .body("currency", equalTo("USD"));
        }

        @Test
        @DisplayName("returns 404 when core-service returns 404")
        void returns404WhenCoreServiceReturns404() {
            coreServiceMock.stubFor(get(urlPathEqualTo("/internal/accounts/unknown/interest-summary"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/problem+json")
                            .withBody("""
                                {"title": "Not Found", "status": 404, "detail": "Interest summary not found"}
                                """)));

            given()
                    .header("Authorization", "Bearer user-jwt")
                    .queryParam("year", "2025")
                    .when()
                    .get("/accounts/{accountId}/interest-summary", "unknown")
                    .then()
                    .statusCode(404)
                    .body("title", equalTo("Not Found"));
        }

        @Test
        @DisplayName("returns 422 for invalid year parameter")
        void returns422ForInvalidYear() {
            given()
                    .queryParam("year", "invalid")
                    .when()
                    .get("/accounts/{accountId}/interest-summary", "account-123")
                    .then()
                    .statusCode(422)
                    .body("detail", containsString("Year must be a valid number"));
        }
    }
}
