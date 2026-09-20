package cl.duoc.xyzbank.interestsservice.interestview.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Circuit Breaker E2E")
class CircuitBreakerE2ETest {

    /*
     * Cases:
     * 1. Circuit breaker opens after multiple failures
     * 2. Circuit breaker rejects calls when OPEN
     * 3. Circuit breaker recovers when core-service is back
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
        registry.add("resilience4j.circuitbreaker.instances.coreServiceInterests.minimumNumberOfCalls", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.coreServiceInterests.slidingWindowSize", () -> "4");
        registry.add("resilience4j.circuitbreaker.instances.coreServiceInterests.failureRateThreshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.coreServiceInterests.waitDurationInOpenState", () -> "2s");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        coreServiceMock.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("opens circuit breaker after multiple failures")
    void opensCircuitBreakerAfterMultipleFailures() {
        coreServiceMock.stubFor(get(urlPathEqualTo("/internal/accounts/account-123/interest-summary"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("coreServiceInterests");

        for (int i = 0; i < 3; i++) {
            given()
                    .queryParam("year", "2025")
                    .when()
                    .get("/accounts/{accountId}/interest-summary", "account-123")
                    .then()
                    .statusCode(503);
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    @DisplayName("rejects calls immediately when circuit breaker is OPEN")
    void rejectsCallsWhenCircuitBreakerIsOpen() {
        coreServiceMock.stubFor(get(urlPathEqualTo("/internal/accounts/account-123/interest-summary"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        for (int i = 0; i < 3; i++) {
            given()
                    .queryParam("year", "2025")
                    .when()
                    .get("/accounts/{accountId}/interest-summary", "account-123");
        }

        coreServiceMock.resetRequests();

        given()
                .queryParam("year", "2025")
                .when()
                .get("/accounts/{accountId}/interest-summary", "account-123")
                .then()
                .statusCode(503)
                .body("detail", containsString("Please try again later"));

        coreServiceMock.verify(0, getRequestedFor(urlPathEqualTo("/internal/accounts/account-123/interest-summary")));
    }

    @Test
    @DisplayName("returns 503 with meaningful message when core-service times out")
    void returns503WhenCoreServiceTimesOut() {
        coreServiceMock.stubFor(get(urlPathEqualTo("/internal/accounts/account-123/interest-summary"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000)));

        given()
                .queryParam("year", "2025")
                .when()
                .get("/accounts/{accountId}/interest-summary", "account-123")
                .then()
                .statusCode(503);
    }
}
