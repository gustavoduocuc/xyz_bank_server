package cl.duoc.xyzbank.interestsservice.interests.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Interest Application Controller E2E")
class InterestApplicationControllerE2ETest {

    /*
     * Cases:
     * 1. Applies annual interest fetching balance and posting interest credit
     * 2. Forwards service JWT and idempotency key on interest-credits POST
     */

    private static WireMockServer coreServiceMock;

    @LocalServerPort
    private int port;

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
    }

    @Nested
    @DisplayName("POST /accounts/{accountId}/interest-applications")
    class PostInterestApplications {

        @Test
        @DisplayName("applies annual interest fetching balance and posting interest credit")
        void appliesAnnualInterestFetchingBalanceAndPostingInterestCredit() {
            coreServiceMock.stubFor(get(urlEqualTo("/internal/accounts/account-123/balance"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "accountId": "account-123",
                                    "balance": 1000.00,
                                    "currency": "USD"
                                }
                                """)));

            coreServiceMock.stubFor(post(urlEqualTo("/internal/accounts/account-123/interest-credits"))
                    .willReturn(aResponse()
                            .withStatus(201)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "transactionId": "tx-1",
                                    "accountId": "account-123",
                                    "year": 2025,
                                    "amount": 35.00,
                                    "currency": "USD",
                                    "occurredOn": "2025-01-01T00:00:00Z",
                                    "newBalance": 1035.00
                                }
                                """)));

            given()
                    .header("Authorization", "Bearer user-jwt")
                    .queryParam("year", "2025")
                    .when()
                    .post("/accounts/{accountId}/interest-applications", "account-123")
                    .then()
                    .statusCode(200)
                    .body("accountId", equalTo("account-123"))
                    .body("year", equalTo(2025))
                    .body("openingBalance", equalTo(1000.00f))
                    .body("closingBalance", equalTo(1035.00f))
                    .body("interestRate", equalTo(0.035f))
                    .body("interestAmount", equalTo(35.00f))
                    .body("currency", equalTo("USD"));

            coreServiceMock.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-123/interest-credits"))
                    .withHeader("Idempotency-Key", com.github.tomakehurst.wiremock.client.WireMock.equalTo(
                            "interest-account-123-2025"))
                    .withHeader("Authorization", containing("Bearer "))
                    .withRequestBody(equalToJson("""
                        {
                            "year": 2025,
                            "amount": 35.00,
                            "currency": "USD",
                            "interestRate": 0.035,
                            "openingBalance": 1000.00,
                            "closingBalance": 1035.00
                        }
                        """)));
        }
    }
}
