package cl.duoc.xyzbank.interestsservice.interests.e2e;

import cl.duoc.xyzbank.interestsservice.testsupport.TestAccessTokens;
import cl.duoc.xyzbank.interestsservice.testsupport.TestAuthServer;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "resilience4j.retry.instances.authServerToken.waitDuration=10ms",
                "resilience4j.circuitbreaker.instances.authServer.waitDurationInOpenState=300ms"
        })
@DisplayName("Interest applications while auth-server is down")
class ServiceTokenOutageE2ETest {

    /*
     * Cases (interests spec, "interests-service's service token request is retried and protected
     * on its own"):
     * 1. With no service token obtainable, an interest application answers service-unavailable
     *    and core-service receives nothing; the auth-server breaker opens while the core-service
     *    breaker stays closed with no recorded failure
     * 2. Once auth-server recovers, retrying the application (same derived Idempotency-Key)
     *    credits the account exactly once
     */

    private static final String CREDIT_URL = "/internal/accounts/account-123/interest-credits";
    private static WireMockServer coreServiceMock;

    @LocalServerPort
    private int port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

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
        TestAuthServer.reset();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        coreServiceMock.stubFor(get(urlEqualTo("/internal/accounts/account-123/balance")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accountId\":\"account-123\",\"balance\":1000.00,\"currency\":\"USD\"}")));
        coreServiceMock.stubFor(post(urlEqualTo(CREDIT_URL)).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"transactionId\":\"tx-1\",\"accountId\":\"account-123\",\"year\":2025,"
                        + "\"amount\":35.00,\"currency\":\"USD\",\"occurredOn\":\"2025-01-01T00:00:00Z\","
                        + "\"newBalance\":1035.00}")));
    }

    @AfterEach
    void restoreAuthServer() {
        TestAuthServer.reset();
    }

    @Test
    @DisplayName("answers service-unavailable without calling core-service and opens only the auth-server breaker")
    void answersServiceUnavailableWithoutCallingCoreServiceAndOpensOnlyTheAuthServerBreaker() {
        TestAuthServer.tokenEndpointUnreachable();
        CircuitBreaker authServer = circuitBreakers.circuitBreaker("authServer");

        for (int request = 0; request < 5 && authServer.getState() != CircuitBreaker.State.OPEN; request++) {
            applyInterest().then().statusCode(503).contentType("application/problem+json");
        }

        assertEquals(CircuitBreaker.State.OPEN, authServer.getState());
        CircuitBreaker coreService = circuitBreakers.circuitBreaker("coreServiceInterests");
        assertEquals(CircuitBreaker.State.CLOSED, coreService.getState());
        assertEquals(0, coreService.getMetrics().getNumberOfFailedCalls());
        coreServiceMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("credits once when the application is retried after auth-server recovers")
    void creditsOnceWhenTheApplicationIsRetriedAfterAuthServerRecovers() {
        TestAuthServer.tokenEndpointUnreachable();
        applyInterest().then().statusCode(503);
        coreServiceMock.verify(0, anyRequestedFor(anyUrl()));

        TestAuthServer.reset();
        TestAuthServer.tokenEndpointIssuesShortLivedTokens();
        applyInterest().then().statusCode(200);

        coreServiceMock.verify(1, postRequestedFor(urlEqualTo(CREDIT_URL))
                .withHeader("Idempotency-Key", equalTo("interest-account-123-2025")));
    }

    private static Response applyInterest() {
        return given()
                .header("Authorization", "Bearer " + TestAccessTokens.interests())
                .queryParam("year", "2025")
                .when()
                .post("/accounts/{accountId}/interest-applications", "account-123");
    }
}
