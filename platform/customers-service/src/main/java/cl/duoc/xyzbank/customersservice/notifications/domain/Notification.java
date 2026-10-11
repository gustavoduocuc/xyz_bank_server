package cl.duoc.xyzbank.customersservice.notifications.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One entry of a customer's feed: a security alert or a confirmed money movement. */
public record Notification(
        String eventId,
        String customerId,
        NotificationKind kind,
        String accountId,
        String type,
        BigDecimal amount,
        String currency,
        OffsetDateTime occurredAt) {
}
