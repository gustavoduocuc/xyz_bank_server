package cl.duoc.xyzbank.coreservice.events.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnExpression(
        "${interests.kafka.enabled:false} || ${app.events.transaction-confirmed.enabled:false}")
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
    private static final String TRANSACTION_CONFIRMED = "TransactionConfirmed";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String creditResultsTopic;
    private final String transactionsConfirmedTopic;

    public OutboxEventRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${interests.kafka.credit-results-topic}") String creditResultsTopic,
            @Value("${app.events.transaction-confirmed.topic}") String transactionsConfirmedTopic) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.creditResultsTopic = creditResultsTopic;
        this.transactionsConfirmedTopic = transactionsConfirmedTopic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:1000}")
    public void publishPending() {
        try {
            pendingEvents().forEach(this::publish);
        } catch (Exception exception) {
            log.warn("Outbox relay will retry on the next tick", exception);
        }
    }

    private void publish(PendingOutboxEvent pending) {
        try {
            String payload = objectMapper.writeValueAsString(pending.message());
            kafkaTemplate.send(pending.topic(), pending.accountId(), payload).get(5, TimeUnit.SECONDS);
            jdbcTemplate.update(
                    "UPDATE outbox_events SET published = TRUE WHERE id = ? AND published = FALSE",
                    pending.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
        } catch (Exception exception) {
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
        }
    }

    private List<PendingOutboxEvent> pendingEvents() {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, schema_version, account_id, period,
                       amount, currency, interest_rate, opening_balance, closing_balance,
                       occurred_on, reason, movement_type
                FROM outbox_events
                WHERE published = FALSE
                ORDER BY occurred_on NULLS LAST, id
                """,
                (row, rowNumber) -> {
                    String eventType = row.getString("event_type");
                    String accountId = row.getString("account_id");
                    Object message = TRANSACTION_CONFIRMED.equals(eventType)
                            ? new TransactionConfirmedMessage(
                                    row.getString("event_id"),
                                    eventType,
                                    row.getInt("schema_version"),
                                    accountId,
                                    row.getString("movement_type"),
                                    row.getBigDecimal("amount"),
                                    row.getString("currency"),
                                    row.getObject("occurred_on", LocalDate.class))
                            : new InterestCreditResultMessage(
                                    row.getString("event_id"),
                                    eventType,
                                    row.getInt("schema_version"),
                                    accountId,
                                    row.getObject("period", Integer.class),
                                    row.getBigDecimal("amount"),
                                    row.getString("currency"),
                                    row.getBigDecimal("interest_rate"),
                                    row.getBigDecimal("opening_balance"),
                                    row.getBigDecimal("closing_balance"),
                                    row.getObject("occurred_on", LocalDate.class),
                                    row.getString("reason"));
                    return new PendingOutboxEvent(
                            row.getObject("id", UUID.class),
                            row.getString("event_id"),
                            accountId,
                            topicFor(eventType),
                            message);
                });
    }

    private String topicFor(String eventType) {
        if (TRANSACTION_CONFIRMED.equals(eventType)) {
            return transactionsConfirmedTopic;
        }
        return creditResultsTopic;
    }

    private record PendingOutboxEvent(
            UUID id, String eventId, String accountId, String topic, Object message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record InterestCreditResultMessage(
            String eventId,
            String eventType,
            int schemaVersion,
            String accountId,
            Integer period,
            BigDecimal amount,
            String currency,
            BigDecimal interestRate,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            LocalDate occurredAt,
            String reason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record TransactionConfirmedMessage(
            String eventId,
            String eventType,
            int schemaVersion,
            String accountId,
            String type,
            BigDecimal amount,
            String currency,
            LocalDate occurredAt) {
    }
}
