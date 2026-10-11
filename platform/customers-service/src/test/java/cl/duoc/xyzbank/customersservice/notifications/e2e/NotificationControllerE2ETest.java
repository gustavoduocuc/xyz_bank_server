package cl.duoc.xyzbank.customersservice.notifications.e2e;

import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationKind;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;
import cl.duoc.xyzbank.customersservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.customersservice.testsupport.TestTokens;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

@DisplayName("The notification feed endpoint over HTTP")
class NotificationControllerE2ETest extends AbstractPostgresIT {

    /*
     * Cases (customer-notifications spec, "A customer's feed is readable through the internal API"):
     * 1. A web token reads its own feed, newest first, with the movement fields on movements
     * 2. The feed holds at most the latest 50 entries
     * 3. A web token for another customer gets 404, like for an unknown customer
     * 4. A token without the feed scope gets 403, and a request without a token gets 401
     * 5. A customers:read service token reads any customer's feed
     */

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationRepository notifications;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("returns the customer's own feed newest first")
    void returnsTheCustomersOwnFeedNewestFirst() {
        String customerId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-08T12:00:00Z");
        notifications.saveIfAbsent(alert("lock-" + customerId, customerId, NotificationKind.CARD_LOCKED, now));
        notifications.saveIfAbsent(new Notification("tx-" + customerId, customerId, NotificationKind.TRANSACTION_CONFIRMED,
                "account-1", "WITHDRAWAL", new BigDecimal("40.00"), "USD", now.plusHours(1)));

        as(TestTokens.web(customerId)).get("/internal/customers/{id}/notifications", customerId)
                .then().statusCode(200)
                .body("kind", contains("TRANSACTION_CONFIRMED", "CARD_LOCKED"))
                .body("[0].accountId", org.hamcrest.Matchers.equalTo("account-1"))
                .body("[0].type", org.hamcrest.Matchers.equalTo("WITHDRAWAL"))
                .body("[0].currency", org.hamcrest.Matchers.equalTo("USD"));
    }

    @Test
    @DisplayName("returns at most the latest 50 entries")
    void returnsAtMostTheLatest50Entries() {
        String customerId = UUID.randomUUID().toString();
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 55; i++) {
            notifications.saveIfAbsent(alert("e" + i + "-" + customerId, customerId, NotificationKind.CARD_LOCKED, start.plusMinutes(i)));
        }

        as(TestTokens.web(customerId)).get("/internal/customers/{id}/notifications", customerId)
                .then().statusCode(200).body("$", hasSize(50));
    }

    @Test
    @DisplayName("answers 404 to a web token for another customer's feed")
    void answers404ToAWebTokenForAnotherCustomersFeed() {
        as(TestTokens.web(UUID.randomUUID().toString()))
                .get("/internal/customers/{id}/notifications", UUID.randomUUID().toString())
                .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @DisplayName("answers 403 without the feed scope and 401 without a token")
    void answers403WithoutTheFeedScopeAnd401WithoutAToken() {
        String customerId = UUID.randomUUID().toString();

        as(TestTokens.withScopes(customerId, List.of("web:accounts:read")))
                .get("/internal/customers/{id}/notifications", customerId).then().statusCode(403);
        given().get("/internal/customers/{id}/notifications", customerId).then().statusCode(401);
    }

    @Test
    @DisplayName("lets a customers:read service token read any customer's feed")
    void letsACustomersReadServiceTokenReadAnyCustomersFeed() {
        String customerId = UUID.randomUUID().toString();
        notifications.saveIfAbsent(alert("lock-" + customerId, customerId, NotificationKind.CARD_LOCKED, OffsetDateTime.now()));

        as(TestTokens.customersAdmin()).get("/internal/customers/{id}/notifications", customerId)
                .then().statusCode(200).body("$", hasSize(1));
    }

    private static Notification alert(String eventId, String customerId, NotificationKind kind, OffsetDateTime at) {
        return new Notification(eventId, customerId, kind, null, null, null, null, at);
    }

    private static io.restassured.specification.RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token);
    }
}
