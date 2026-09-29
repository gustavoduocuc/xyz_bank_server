package cl.duoc.xyzbank.bffatm.withdrawal.e2e;

import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Withdrawal controller")
class WithdrawalControllerE2ETest {

    private static final String TERMINAL_ID = "atm-terminal-001";

    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AuthServerStub AUTH_SERVER = new AuthServerStub();

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void coreServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.base-url", CORE_SERVICE::baseUrl);
        AUTH_SERVER.register(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.config = RestAssured.config()
                .sslConfig(SSLConfig.sslConfig()
                        .keyStore("tls/terminal-keystore.p12", "xyzbank-dev")
                        .and()
                        .relaxedHTTPSValidation());
        CORE_SERVICE.resetAll();
    }

    @AfterAll
    static void stopCoreServiceStub() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    private RequestSpecification asAtm() {
        return given().header("Authorization", "Bearer " + tokenAdapter.issue("customer-1", Channel.ATM, TERMINAL_ID));
    }

    @Test
    @DisplayName("returns a successful withdrawal")
    void returnsASuccessfulWithdrawal() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));

        asAtm()
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201)
                .body("transactionId", equalTo("tx-1"))
                .body("newBalance", equalTo(210.00f));
    }

    @Test
    @DisplayName("rejects a missing idempotency key")
    void rejectsAMissingIdempotencyKey() {
        asAtm()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("relays a core-service conflict as 409")
    void relaysACoreServiceConflictAs409() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Conflict\"}")));

        asAtm()
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(409)
                .contentType("application/problem+json");
    }

    @Test
    @DisplayName("propagates a generated correlation id when the inbound header is absent")
    void propagatesAGeneratedCorrelationIdWhenTheInboundHeaderIsAbsent() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));

        String correlationId = asAtm()
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201)
                .header("X-Correlation-Id", not(emptyOrNullString()))
                .extract()
                .header("X-Correlation-Id");

        CORE_SERVICE.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withHeader("X-Correlation-Id", WireMock.equalTo(correlationId)));
    }

    @Test
    @DisplayName("sends no static service credential on outbound core-service calls")
    void sendsNoStaticServiceCredentialOnOutboundCoreServiceCalls() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));

        asAtm()
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201);

        CORE_SERVICE.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withoutHeader("X-Service-Credential"));
    }

    @Test
    @DisplayName("calls core-service with its own client token")
    void callsCoreServiceWithItsOwnClientToken() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"transactionId\":\"tx-1\",\"accountId\":\"account-1\",\"amount\":40.00,\"currency\":\"USD\",\"occurredOn\":\"2026-01-01\",\"newBalance\":210.00}")));
        String token = tokenAdapter.issue("customer-1", Channel.ATM, TERMINAL_ID);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(201);

        CORE_SERVICE.verify(postRequestedFor(urlEqualTo("/internal/accounts/account-1/withdrawals"))
                .withHeader("Authorization", WireMock.equalTo("Bearer " + AuthServerStub.ACCESS_TOKEN)));
    }

    @Test
    @DisplayName("rejects a session bound to a different terminal than the one presenting it")
    void rejectsASessionBoundToADifferentTerminal() {
        given()
                .header("Authorization", "Bearer " + tokenAdapter.issue("customer-1", Channel.ATM, "some-other-terminal"))
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"amount\":40.00,\"currency\":\"USD\"}")
                .when()
                .post("/accounts/{accountId}/withdrawals", "account-1")
                .then()
                .statusCode(422)
                .contentType("application/problem+json");
    }
}
