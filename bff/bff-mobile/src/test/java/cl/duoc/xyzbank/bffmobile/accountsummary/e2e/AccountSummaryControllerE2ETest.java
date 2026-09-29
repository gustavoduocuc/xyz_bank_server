package cl.duoc.xyzbank.bffmobile.accountsummary.e2e;

import cl.duoc.xyzbank.bffmobile.auth.testsupport.MockOidcProvider;
import cl.duoc.xyzbank.bffmobile.auth.testsupport.TestSessions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Account Summary controller")
class AccountSummaryControllerE2ETest {

    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final MockOidcProvider AUTHORIZATION_SERVER = new MockOidcProvider();

    static {
        CORE_SERVICE.start();
        AUTHORIZATION_SERVER.start();
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
        CORE_SERVICE.resetAll();
        stubSummary();
    }

    @AfterAll
    static void stopStubs() {
        CORE_SERVICE.stop();
        AUTHORIZATION_SERVER.stop();
    }

    private RequestSpecification asDevice(String deviceId) {
        return given()
                .header("Authorization", "Bearer " + TestSessions.mobileSessionFor("customer-1", deviceId))
                .header("X-Device-Id", deviceId);
    }

    @Test
    @DisplayName("returns a flattened account summary")
    void returnsAFlattenedAccountSummary() {
        asDevice("device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200)
                .body("balance", equalTo(500.00f))
                .body("currency", equalTo("USD"))
                .body("transactions", hasSize(1))
                .body("transactions[0].id", equalTo("tx-1"))
                .body("profile", nullValue())
                .body("nextCursor", nullValue());
    }

    @Test
    @DisplayName("responds with not-found for an unknown account")
    void respondsWithNotFoundForAnUnknownAccount() {
        CORE_SERVICE.resetAll();
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/unknown/balance"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Account unknown not found\"}")));

        asDevice("device-1")
                .when()
                .get("/accounts/{accountId}/summary", "unknown")
                .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("rejects a caller whose channel is not mobile")
    void rejectsACallerWhoseChannelIsNotMobile() {
        given()
                .header("Authorization", "Bearer " + TestSessions.webSessionFor("customer-1"))
                .header("X-Device-Id", "device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(403)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("rejects a token presented with a mismatched device id")
    void rejectsATokenPresentedWithAMismatchedDeviceId() {
        given()
                .header("Authorization", "Bearer " + TestSessions.mobileSessionFor("customer-1", "device-1"))
                .header("X-Device-Id", "device-2")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("ignores filter and pagination query parameters")
    void ignoresFilterAndPaginationQueryParameters() {
        asDevice("device-1")
                .queryParam("from", "2020-01-01")
                .queryParam("to", "2026-12-31")
                .queryParam("type", "CREDIT")
                .queryParam("cursor", "ignored")
                .queryParam("pageSize", "100")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200)
                .body("transactions", hasSize(1))
                .body("nextCursor", nullValue());
    }

    @Test
    @DisplayName("propagates the correlation id to core-service")
    void propagatesTheCorrelationIdToCoreService() {
        asDevice("device-1")
                .header("X-Correlation-Id", "corr-mobile-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", equalTo("corr-mobile-1"));

        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/balance"))
                .withHeader("X-Correlation-Id", WireMock.equalTo("corr-mobile-1")));
        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .withHeader("X-Correlation-Id", WireMock.equalTo("corr-mobile-1")));
    }

    @Test
    @DisplayName("propagates a generated correlation id when the inbound header is absent")
    void propagatesAGeneratedCorrelationIdWhenTheInboundHeaderIsAbsent() {
        String correlationId = asDevice("device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", not(emptyOrNullString()))
                .extract()
                .header("X-Correlation-Id");

        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/balance"))
                .withHeader("X-Correlation-Id", WireMock.equalTo(correlationId)));
        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .withHeader("X-Correlation-Id", WireMock.equalTo(correlationId)));
    }

    @Test
    @DisplayName("sends no static service credential on outbound core-service calls")
    void sendsNoStaticServiceCredentialOnOutboundCoreServiceCalls() {
        asDevice("device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200);

        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/balance"))
                .withoutHeader("X-Service-Credential"));
        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .withoutHeader("X-Service-Credential"));
    }

    @Test
    @DisplayName("forwards the caller's session token as a bearer token on every outbound core-service call")
    void forwardsTheCallersSessionTokenAsABearerTokenOnEveryOutboundCoreServiceCall() {
        String token = TestSessions.mobileSessionFor("customer-1", "device-1");

        given()
                .header("Authorization", "Bearer " + token)
                .header("X-Device-Id", "device-1")
                .when()
                .get("/accounts/{accountId}/summary", "account-1")
                .then()
                .statusCode(200);

        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/balance"))
                .withHeader("Authorization", WireMock.equalTo("Bearer " + token)));
        CORE_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .withHeader("Authorization", WireMock.equalTo("Bearer " + token)));
    }

    private static void stubSummary() {
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"account-1\",\"balance\":500.00,\"currency\":\"USD\"}")));
        CORE_SERVICE.stubFor(get(urlEqualTo("/internal/accounts/account-1/transactions?pageSize=5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"items\":[{\"id\":\"tx-1\",\"type\":\"DEBIT\",\"amount\":50.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"description\":null}],\"nextCursor\":null}")));
    }
}
