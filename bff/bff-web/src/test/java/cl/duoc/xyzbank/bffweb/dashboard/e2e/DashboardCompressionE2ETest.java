package cl.duoc.xyzbank.bffweb.dashboard.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffweb.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Dashboard controller's response compression")
class DashboardCompressionE2ETest {

    /*
     * Cases:
     * 1. A dashboard response large enough to cross Boot's compression threshold is gzipped
     *    when the client advertises Accept-Encoding: gzip
     */

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
        stubCustomerWithManyAccounts();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
    }

    private void stubCustomerWithManyAccounts() {
        // Boot's default server.compression.min-response-size is 2KB; this many accounts with a
        // non-null description each comfortably clears it.
        int accountCount = 20;
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"customer-1\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));

        String accountsJson = IntStream.range(0, accountCount)
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

        IntStream.range(0, accountCount).forEach(i -> CORE_SERVICE.stubFor(
                get(urlEqualTo("/internal/accounts/account-" + i + "/transactions?pageSize=5"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"items\":[{\"id\":\"tx-" + i
                                        + "\",\"type\":\"DEBIT\",\"amount\":10.00,\"currency\":\"USD\","
                                        + "\"occurredOn\":\"2026-01-01\","
                                        + "\"description\":\"Monthly recurring payment number " + i
                                        + "\"}],\"nextCursor\":null}"))));
    }

    @Test
    @DisplayName("gzips a response body that exceeds the compression threshold")
    void gzipsAResponseBodyThatExceedsTheCompressionThreshold() {
        String sessionToken = TestSessions.webSessionFor("customer-1");

        given()
                .cookie("session", sessionToken)
                .header("Accept-Encoding", "gzip")
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(200)
                .header("Content-Encoding", equalTo("gzip"));
    }
}
