package cl.duoc.xyzbank.coreservice.events.infrastructure.persistence;

import cl.duoc.xyzbank.coreservice.events.application.dto.TransactionConfirmed;
import cl.duoc.xyzbank.coreservice.events.application.ports.TransactionConfirmedPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.events.transaction-confirmed.enabled", havingValue = "true")
public class OutboxTransactionConfirmedPublisher implements TransactionConfirmedPublisher {

    private final JdbcTemplate jdbcTemplate;

    public OutboxTransactionConfirmedPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void publish(TransactionConfirmed event) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, account_id, customer_id,
                    amount, currency, occurred_on, movement_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                event.eventId(),
                "TransactionConfirmed",
                1,
                UUID.fromString(event.accountId()),
                UUID.fromString(event.customerId()),
                event.amount(),
                event.currency(),
                event.occurredAt(),
                event.type().name());
    }
}
