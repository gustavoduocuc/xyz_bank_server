package cl.duoc.xyzbank.customersservice.notifications.infrastructure.kafka;

import cl.duoc.xyzbank.customersservice.notifications.application.RecordNotificationUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Consumes the two topics. A record that cannot be read throws, so the error handler retries it
 * and then dead-letters it; records that are well formed but not for the feed are skipped by the use case.
 */
@Component
public class NotificationListeners {

    private final RecordNotificationUseCase recordNotification;
    private final ObjectMapper objectMapper;

    public NotificationListeners(RecordNotificationUseCase recordNotification, ObjectMapper objectMapper) {
        this.recordNotification = recordNotification;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notifications.kafka.transactions-topic:transactions.confirmed}")
    public void onTransactionConfirmed(String payload) throws IOException {
        JsonNode event = objectMapper.readTree(payload);
        recordNotification.recordMovement(
                required(event, "eventId"),
                text(event, "customerId"),
                text(event, "accountId"),
                text(event, "type"),
                new BigDecimal(required(event, "amount")),
                text(event, "currency"),
                LocalDate.parse(required(event, "occurredAt")));
    }

    @KafkaListener(topics = "${notifications.kafka.alerts-topic:security.alerts}")
    public void onSecurityAlert(String payload) throws IOException {
        JsonNode event = objectMapper.readTree(payload);
        recordNotification.recordAlert(
                required(event, "eventId"),
                required(event, "alertType"),
                required(event, "customerId"),
                OffsetDateTime.parse(required(event, "occurredAt")));
    }

    private static String required(JsonNode event, String field) {
        String value = text(event, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + field);
        }
        return value;
    }

    private static String text(JsonNode event, String field) {
        JsonNode node = event.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
