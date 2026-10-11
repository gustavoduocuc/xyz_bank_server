package cl.duoc.xyzbank.customersservice.notifications.application;

import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationKind;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Turns platform events into feed entries, once per event id. */
public class RecordNotificationUseCase {

    private final NotificationRepository notifications;

    public RecordNotificationUseCase(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** A confirmed movement; events written before the customer id was added carry none and are skipped. */
    public void recordMovement(
            String eventId,
            String customerId,
            String accountId,
            String movementType,
            BigDecimal amount,
            String currency,
            LocalDate occurredOn) {
        if (customerId == null || customerId.isBlank()) {
            return;
        }
        notifications.saveIfAbsent(new Notification(
                eventId,
                customerId,
                NotificationKind.TRANSACTION_CONFIRMED,
                accountId,
                movementType,
                amount,
                currency,
                occurredOn.atStartOfDay().atOffset(ZoneOffset.UTC)));
    }

    /** A security alert; alert types this service does not know yet are ignored. */
    public void recordAlert(String eventId, String alertType, String customerId, OffsetDateTime occurredAt) {
        NotificationKind kind;
        try {
            kind = NotificationKind.valueOf(alertType);
        } catch (IllegalArgumentException exception) {
            return;
        }
        if (kind == NotificationKind.TRANSACTION_CONFIRMED) {
            return;
        }
        notifications.saveIfAbsent(new Notification(eventId, customerId, kind, null, null, null, null, occurredAt));
    }
}
