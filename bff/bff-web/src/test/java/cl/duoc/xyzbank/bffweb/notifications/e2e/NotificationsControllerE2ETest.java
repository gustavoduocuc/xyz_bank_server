package cl.duoc.xyzbank.bffweb.notifications.e2e;

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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The Notifications controller")
class NotificationsControllerE2ETest {

    /*
     * Cases (bff-web spec, "Notification feed per customer"):
     * 1. A web customer reads their feed, as customers-service returns it, with their token relayed
     * 2. Another customer's feed is answered 404 (customers-service hides it) and surfaced as is
     * 3. A non-web caller is rejected
     */

    private static final MockOidcProvider OIDC_PROVIDER = new MockOidcProvider();
    private static final WireMockServer CUSTOMERS_SERVICE = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        CUSTOMERS_SERVICE.start();
    }

    @BeforeAll
    static void startAuthorizationServer() {
        OIDC_PROVIDER.start();
    }

    @AfterAll
    static void stopServers() {
        OIDC_PROVIDER.stop();
        CUSTOMERS_SERVICE.stop();
    }

    @DynamicPropertySource
    static void customersServiceBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("customers-service.base-url", CUSTOMERS_SERVICE::baseUrl);
        registry.add("core-service.base-url", CUSTOMERS_SERVICE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        CUSTOMERS_SERVICE.resetAll();
    }

    @Test
    @DisplayName("returns the customer's feed from customers-service and relays the customer's token")
    void returnsTheCustomersFeedFromCustomersServiceAndRelaysTheCustomersToken() {
        String session = TestSessions.webSessionFor("customer-1");
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-1/notifications"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"eventId":"tx-1","kind":"TRANSACTION_CONFIRMED","occurredAt":"2026-10-09T00:00:00Z",
                          "accountId":"account-1","type":"WITHDRAWAL","amount":40.00,"currency":"USD"},
                         {"eventId":"alert-1","kind":"CARD_LOCKED","occurredAt":"2026-10-08T12:00:00Z"}]""")));

        given().cookie("session", session)
                .get("/customers/{customerId}/notifications", "customer-1")
                .then().statusCode(200)
                .body("kind", contains("TRANSACTION_CONFIRMED", "CARD_LOCKED"))
                .body("[0].type", org.hamcrest.Matchers.equalTo("WITHDRAWAL"));

        CUSTOMERS_SERVICE.verify(getRequestedFor(urlEqualTo("/internal/customers/customer-1/notifications"))
                .withHeader("Authorization", equalTo("Bearer " + session)));
    }

    @Test
    @DisplayName("surfaces customers-service's 404 for another customer's feed")
    void surfacesCustomersServices404ForAnotherCustomersFeed() {
        CUSTOMERS_SERVICE.stubFor(get(urlEqualTo("/internal/customers/customer-2/notifications"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"detail\":\"Customer customer-2 not found\"}")));

        given().cookie("session", TestSessions.webSessionFor("customer-1"))
                .get("/customers/{customerId}/notifications", "customer-2")
                .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @DisplayName("rejects a caller whose channel is not web")
    void rejectsACallerWhoseChannelIsNotWeb() {
        given().cookie("session", TestSessions.mobileSessionFor("customer-1"))
                .get("/customers/{customerId}/notifications", "customer-1")
                .then().statusCode(403).contentType("application/problem+json");
    }
}
