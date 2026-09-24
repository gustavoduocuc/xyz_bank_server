package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCreditOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(InterestCreditOutboxRelay.class);

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String creditResultsTopic;

    public InterestCreditOutboxRelay(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${interests.kafka.credit-results-topic}") String creditResultsTopic) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.creditResultsTopic = creditResultsTopic;
    }

    @Scheduled(fixedDelayString = "${interests.outbox.relay-delay-ms:1000}")
    public void publishPending() {
        try {
            pendingResults().forEach(this::publish);
        } catch (Exception exception) {
            log.warn("Interest credit outbox relay will retry on the next tick", exception);
        }
    }

    private void publish(PendingCreditResult pending) {
        try {
            String payload = objectMapper.writeValueAsString(pending.message());
            kafkaTemplate.send(creditResultsTopic, pending.accountId(), payload).get(5, TimeUnit.SECONDS);
            jdbcTemplate.update(
                    "UPDATE outbox_events SET published = TRUE WHERE id = ? AND published = FALSE",
                    pending.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interest credit result {} stays in the outbox until the next relay attempt", pending.eventId(), exception);
        } catch (Exception exception) {
            log.warn("Interest credit result {} stays in the outbox until the next relay attempt", pending.eventId(), exception);
        }
    }

    private List<PendingCreditResult> pendingResults() {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, event_type, schema_version, account_id, period,
                       amount, currency, interest_rate, opening_balance, closing_balance,
                       occurred_on, reason
                FROM outbox_events
                WHERE published = FALSE
                """,
                (row, rowNumber) -> new PendingCreditResult(
                        row.getObject("id", UUID.class),
                        row.getString("event_id"),
                        row.getString("account_id"),
                        new InterestCreditResultMessage(
                                row.getString("event_id"),
                                row.getString("event_type"),
                                row.getInt("schema_version"),
                                row.getString("account_id"),
                                row.getObject("period", Integer.class),
                                row.getBigDecimal("amount"),
                                row.getString("currency"),
                                row.getBigDecimal("interest_rate"),
                                row.getBigDecimal("opening_balance"),
                                row.getBigDecimal("closing_balance"),
                                row.getObject("occurred_on", LocalDate.class),
                                row.getString("reason"))));
    }

    private record PendingCreditResult(UUID id, String eventId, String accountId, InterestCreditResultMessage message) {
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
}
