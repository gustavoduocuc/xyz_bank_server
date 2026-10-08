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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnExpression(
        "${interests.kafka.enabled:false} || ${app.events.transaction-confirmed.enabled:false}")
public class OutboxEventRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRelay.class);
    private static final String TRANSACTION_CONFIRMED = "TransactionConfirmed";
    private static final Set<String> INTEREST_CREDIT_RESULTS = Set.of(
            "InterestCreditApplied",
            "InterestCreditRejected");

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String creditResultsTopic;
    private final String transactionsConfirmedTopic;

    public OutboxEventRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${interests.kafka.credit-results-topic}") String creditResultsTopic,
            @Value("${app.events.transaction-confirmed.topic}") String transactionsConfirmedTopic) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.creditResultsTopic = creditResultsTopic;
        this.transactionsConfirmedTopic = transactionsConfirmedTopic;
    }

    /**
     * Sends pending events oldest first. When an event fails to send, the later events of the
     * same account wait for the next run so they cannot overtake it (per-account order on the
     * topics); events of other accounts keep flowing. Rows are locked with SKIP LOCKED until they
     * are marked published, so relays of several replicas never send the same event twice.
     */
    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:1000}")
    public void publishPending() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Set<String> accountsWithAFailedSend = new HashSet<>();
                for (PendingOutboxEvent pending : pendingEvents()) {
                    if (accountsWithAFailedSend.contains(pending.accountId())) {
                        continue;
                    }
                    if (!publish(pending)) {
                        accountsWithAFailedSend.add(pending.accountId());
                    }
                }
            });
        } catch (Exception exception) {
            log.warn("Outbox relay will retry on the next tick", exception);
        }
    }

    private boolean publish(PendingOutboxEvent pending) {
        try {
            String payload = objectMapper.writeValueAsString(pending.message());
            kafkaTemplate.send(pending.topic(), pending.accountId(), payload).get(5, TimeUnit.SECONDS);
            jdbcTemplate.update(
                    "UPDATE outbox_events SET published = TRUE WHERE id = ? AND published = FALSE",
                    pending.id());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
            return false;
        } catch (Exception exception) {
            log.warn("Outbox event {} stays until the next relay attempt", pending.eventId(), exception);
            return false;
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
                ORDER BY seq
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """,
                (row, rowNumber) -> mapPending(row)).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private PendingOutboxEvent mapPending(java.sql.ResultSet row) throws java.sql.SQLException {
        String eventType = row.getString("event_type");
        String eventId = row.getString("event_id");
        String accountId = row.getString("account_id");
        UUID id = row.getObject("id", UUID.class);
        if (TRANSACTION_CONFIRMED.equals(eventType)) {
            return new PendingOutboxEvent(
                    id,
                    eventId,
                    accountId,
                    transactionsConfirmedTopic,
                    new TransactionConfirmedMessage(
                            eventId,
                            eventType,
                            row.getInt("schema_version"),
                            accountId,
                            row.getString("movement_type"),
                            row.getBigDecimal("amount"),
                            row.getString("currency"),
                            row.getObject("occurred_on", LocalDate.class)));
        }
        if (INTEREST_CREDIT_RESULTS.contains(Objects.requireNonNullElse(eventType, ""))) {
            return new PendingOutboxEvent(
                    id,
                    eventId,
                    accountId,
                    creditResultsTopic,
                    new InterestCreditResultMessage(
                            eventId,
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
                            row.getString("reason")));
        }
        log.warn(
                "Skipping unknown outbox event_type={} eventId={} until it is recognized",
                eventType,
                eventId);
        return null;
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
