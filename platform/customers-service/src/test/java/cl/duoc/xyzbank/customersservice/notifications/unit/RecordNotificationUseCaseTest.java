package cl.duoc.xyzbank.customersservice.notifications.unit;

import cl.duoc.xyzbank.customersservice.notifications.application.RecordNotificationUseCase;
import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The RecordNotification use case")
class RecordNotificationUseCaseTest {

    /*
     * Cases (customer-notifications spec):
     * 1. A confirmed movement becomes a TRANSACTION_CONFIRMED entry for its customer
     * 2. A security alert becomes an entry of its kind for its customer
     * 3. Recording the same event id again leaves one entry
     * 4. A movement without customer id, and an alert type the service does not know, are skipped
     */

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T12:00:00Z");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final RecordNotificationUseCase useCase = new RecordNotificationUseCase(repository);

    @Test
    @DisplayName("records a confirmed movement for its customer")
    void recordsAConfirmedMovementForItsCustomer() {
        useCase.recordMovement("tx-1", "c-1", "a-1", "WITHDRAWAL", new BigDecimal("40.00"), "USD", LocalDate.parse("2026-10-08"));

        List<Notification> feed = repository.latestOf("c-1", 50);
        assertEquals(1, feed.size());
        assertEquals(NotificationKind.TRANSACTION_CONFIRMED, feed.getFirst().kind());
        assertEquals("a-1", feed.getFirst().accountId());
        assertEquals("WITHDRAWAL", feed.getFirst().type());
        assertEquals(0, new BigDecimal("40.00").compareTo(feed.getFirst().amount()));
    }

    @Test
    @DisplayName("records a security alert as an entry of its kind")
    void recordsASecurityAlertAsAnEntryOfItsKind() {
        useCase.recordAlert("alert-1", "CARD_LOCKED", "c-1", NOW);
        useCase.recordAlert("alert-2", "REFRESH_TOKEN_REUSE", "c-1", NOW.plusMinutes(1));

        assertEquals(
                List.of(NotificationKind.REFRESH_TOKEN_REUSE, NotificationKind.CARD_LOCKED),
                repository.latestOf("c-1", 50).stream().map(Notification::kind).toList());
    }

    @Test
    @DisplayName("keeps one entry when the same event id is recorded again")
    void keepsOneEntryWhenTheSameEventIdIsRecordedAgain() {
        useCase.recordAlert("alert-1", "CARD_LOCKED", "c-1", NOW);
        useCase.recordAlert("alert-1", "CARD_LOCKED", "c-1", NOW);
        useCase.recordMovement("tx-1", "c-1", "a-1", "WITHDRAWAL", BigDecimal.TEN, "USD", LocalDate.parse("2026-10-08"));
        useCase.recordMovement("tx-1", "c-1", "a-1", "WITHDRAWAL", BigDecimal.TEN, "USD", LocalDate.parse("2026-10-08"));

        assertEquals(2, repository.size());
    }

    @Test
    @DisplayName("skips a movement without customer id and an unknown alert type")
    void skipsAMovementWithoutCustomerIdAndAnUnknownAlertType() {
        useCase.recordMovement("tx-old", null, "a-1", "WITHDRAWAL", BigDecimal.TEN, "USD", LocalDate.parse("2026-10-08"));
        useCase.recordAlert("alert-x", "DEVICE_REVOKED", "c-1", NOW);

        assertEquals(0, repository.size());
    }
}
