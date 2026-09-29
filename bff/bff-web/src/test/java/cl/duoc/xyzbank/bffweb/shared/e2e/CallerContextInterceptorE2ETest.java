package cl.duoc.xyzbank.bffweb.shared.e2e;

import cl.duoc.xyzbank.bffweb.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffweb.auth.testsupport.TestSessions;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("bff-web's caller context")
class CallerContextInterceptorE2ETest {

    /*
     * Cases:
     * 1. A valid web access token is accepted
     * 2. A token issued to bff-mobile is rejected
     * 3. An expired web access token is rejected
     * 4. A web access token signed by a foreign key is rejected
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        OIDC_PROVIDER.start();
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
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        OIDC_PROVIDER.resetAll();
        CORE_SERVICE.resetAll();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CORE_SERVICE.stop();
    }

    @Test
    @DisplayName("accepts a valid web access token")
    void acceptsAValidWebAccessToken() {
        stubDashboardFor("customer-1");

        given()
                .cookie("session", TestSessions.webSessionFor("customer-1"))
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("rejects a token issued to bff-mobile")
    void rejectsATokenIssuedToBffMobile() {
        given()
                .cookie("session", TestSessions.mobileSessionFor("customer-1"))
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("rejects an expired web access token")
    void rejectsAnExpiredWebAccessToken() {
        given()
                .cookie("session", TestSessions.expiredWebSessionFor("customer-1"))
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(422);
    }

    @Test
    @DisplayName("rejects a web access token signed by a foreign key")
    void rejectsAWebAccessTokenSignedByAForeignKey() {
        given()
                .cookie("session", TestSessions.foreignSignedWebSessionFor("customer-1"))
                .when()
                .get("/customers/{customerId}/dashboard", "customer-1")
                .then()
                .statusCode(422);
    }

    private static void stubDashboardFor(String customerId) {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/" + customerId))
                .willReturn(json("{\"id\":\"" + customerId + "\",\"fullName\":\"Ana Perez\",\"email\":\"ana@example.com\"}")));
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/customers/" + customerId + "/accounts"))
                .willReturn(json(
                        "[{\"id\":\"account-1\",\"accountNumber\":\"1000000001\",\"balance\":500.00,\"currency\":\"USD\"}]")));
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .willReturn(json("{\"items\":[],\"nextCursor\":null}")));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
