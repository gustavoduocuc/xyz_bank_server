package cl.duoc.xyzbank.bffweb.dashboard.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffweb.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Dashboard controller's latency under a multi-account fan-out")
class DashboardLatencyE2ETest {

    /*
     * Cases:
     * 1. Per-account transaction calls run concurrently: total latency stays close to one
     *    call's delay, not the sum of every account's delay
     * 2. The response body shape is unaffected by running those calls concurrently
     */

    private static final int ACCOUNT_COUNT = 5;
    private static final Duration PER_CALL_DELAY = Duration.ofMillis(300);

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CORE_SERVICE.start();
    }

    @BeforeAll
    static void startAuthorizationServer() {
        OIDC_PROVIDER.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssuredAndStubs() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        CORE_SERVICE.resetAll();
        stubCustomerWithAccountsAndDelayedTransactions();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
    }

    private void stubCustomerWithAccountsAndDelayedTransactions() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));

        String accountsJson = IntStream.range(0, ACCOUNT_COUNT)
                .mapToObj(i -> "{\"id\":\"account-" + i + "\",\"accountNumber\":\"100000000" + i
                        + "\",\"balance\":500.00,\"currency\":\"USD\"}")
                .reduce((a, b) -> a + "," + b)
                .map(items -> "[" + items + "]")
                .orElseThrow();
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1/accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(accountsJson)));

        IntStream.range(0, ACCOUNT_COUNT).forEach(i -> CORE_SERVICE.stubFor(
                get(urlEqualTo("/internal/accounts/account-" + i + "/transactions?pageSize=5"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withFixedDelay((int) PER_CALL_DELAY.toMillis())
                                .withBody("{\"items\":[{\"id\":\"tx-" + i
                                        + "\",\"type\":\"DEBIT\",\"amount\":10.00,\"currency\":\"USD\","
                                        + "\"occurredOn\":\"2026-01-01\",\"description\":null}],\"nextCursor\":null}"))));
    }

    @Test
    @DisplayName("responds in well under the sum of every account's transaction-fetch delay")
    void respondsInWellUnderTheSumOfEveryAccountsTransactionFetchDelay() {
        String sessionToken = TestSessions.webSessionFor("customer-1");

        Instant start = Instant.now();
        Response response = given()
                .cookie("session", sessionToken)
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1");
        Duration elapsed = Duration.between(start, Instant.now());

        response.then().statusCode(200);

        Duration serialUpperBound = PER_CALL_DELAY.multipliedBy(ACCOUNT_COUNT);
        assertTrue(
                elapsed.compareTo(serialUpperBound) < 0,
                () -> "expected concurrent execution well under " + serialUpperBound + " (serial would be >= that) "
                        + "but the request took " + elapsed);
    }

    @Test
    @DisplayName("returns the same response shape whether calls run concurrently or not")
    void returnsTheSameResponseShapeWhetherCallsRunConcurrentlyOrNot() {
        String sessionToken = TestSessions.webSessionFor("customer-1");

        given()
                .cookie("session", sessionToken)
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(200)
                .body("profile.id", equalTo("customer-1"))
                .body("profile.fullName", equalTo("Ana Perez"))
                .body("accounts", hasSize(ACCOUNT_COUNT))
                .body("accounts[0].transactions", hasSize(1))
                .body("accounts[0].transactions[0].id", equalTo("tx-0"))
                .body("accounts[4].transactions[0].id", equalTo("tx-4"));
    }
}
