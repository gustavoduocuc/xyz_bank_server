package cl.duoc.xyzbank.interestsservice.shared.e2e;

import cl.duoc.xyzbank.interestsservice.testsupport.TestAccessTokens;
import cl.duoc.xyzbank.interestsservice.testsupport.TestAuthServer;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;

/**
 * interests-service as a resource server (adopt-oauth2-tokens-between-services, interests spec
 * "interests-service accepts only valid platform tokens").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("interests-service's inbound token validation")
class InboundTokenE2ETest {

    /*
     * Cases:
     * 1. A valid web token reads the interest summary, and that same token is relayed to core-service
     * 2. A summary request without a token is rejected with 401 and calls nothing downstream
     * 3. A mobile token (not issued for interests-service) is rejected with 401
     * 4. An expired or foreign-signed token is rejected with 401
     * 5. An interest application with a web token is rejected with 403
     * 6. An interest application with an interests:write token is processed
     */

    private static final String SUMMARY_PATH = "/internal/accounts/account-1/interest-summary";
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
        registry.add("auth.jwk-set-uri", TestAuthServer::jwkSetUri);
        registry.add("auth.token-uri", TestAuthServer::tokenUri);
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        coreServiceMock.resetAll();
        coreServiceMock.stubFor(get(urlPathEqualTo(SUMMARY_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"accountId":"account-1","year":2025,"openingBalance":1000.00,"closingBalance":1025.00,
                     "annualRate":2.5,"interestAmount":25.00,"currency":"USD"}
                    """)));
    }

    @Test
    @DisplayName("serves the summary to a valid web token and relays that same token to core-service")
    void servesTheSummaryToAValidWebTokenAndRelaysIt() {
        String webToken = TestAccessTokens.web("customer-1");

        given().header("Authorization", "Bearer " + webToken)
                .get("/accounts/account-1/interest-summary?year=2025")
                .then().statusCode(200);

        coreServiceMock.verify(getRequestedFor(urlPathEqualTo(SUMMARY_PATH))
                .withHeader("Authorization", equalTo("Bearer " + webToken)));
    }

    @Test
    @DisplayName("rejects a summary request without a token and calls nothing downstream")
    void rejectsASummaryRequestWithoutATokenAndCallsNothingDownstream() {
        given().get("/accounts/account-1/interest-summary?year=2025").then().statusCode(401);

        coreServiceMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("rejects a mobile token, which is not issued for interests-service")
    void rejectsAMobileToken() {
        given().header("Authorization", "Bearer " + TestAccessTokens.mobile("customer-1", "D1"))
                .get("/accounts/account-1/interest-summary?year=2025")
                .then().statusCode(401);
    }

    static Stream<String> invalidTokens() {
        return Stream.of(
                TestAccessTokens.token("bff-web", Channel.WEB).subject("customer-1")
                        .expiredAt(Instant.parse("2020-01-01T00:00:00Z")).sign(),
                TestAccessTokens.token("bff-web", Channel.WEB).subject("customer-1").signedWithForeignKey().sign());
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    @DisplayName("rejects an expired or foreign-signed token with 401")
    void rejectsAnExpiredOrForeignSignedToken(String token) {
        given().header("Authorization", "Bearer " + token)
                .get("/accounts/account-1/interest-summary?year=2025")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("rejects an interest application with a web token with 403")
    void rejectsAnInterestApplicationWithAWebToken() {
        given().header("Authorization", "Bearer " + TestAccessTokens.web("customer-1"))
                .post("/accounts/account-1/interest-applications?year=2025")
                .then().statusCode(403);

        coreServiceMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    @DisplayName("processes an interest application with an interests:write token")
    void processesAnInterestApplicationWithAnInterestsWriteToken() {
        coreServiceMock.stubFor(get(urlEqualTo("/internal/accounts/account-1/balance")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"accountId\":\"account-1\",\"balance\":1000.00,\"currency\":\"USD\"}")));
        coreServiceMock.stubFor(post(urlEqualTo("/internal/accounts/account-1/interest-credits")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"year\":2025,"
                        + "\"interestAmount\":35.00,\"newBalance\":1035.00,\"currency\":\"USD\"}")));

        given().header("Authorization", "Bearer " + TestAccessTokens.interests())
                .post("/accounts/account-1/interest-applications?year=2025")
                .then().statusCode(200);
    }
}
