package cl.duoc.xyzbank.customersservice.notifications.application;

import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String eventId,
        String kind,
        OffsetDateTime occurredAt,
        String accountId,
        String type,
        BigDecimal amount,
        String currency) {

    static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.eventId(),
                notification.kind().name(),
                notification.occurredAt(),
                notification.accountId(),
                notification.type(),
                notification.amount(),
                notification.currency());
    }
}
