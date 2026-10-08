package cl.duoc.xyzbank.coreservice.events.infrastructure.persistence;

import cl.duoc.xyzbank.coreservice.events.application.ports.SecurityAlertPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Writes the alert to the outbox in the caller's transaction; the relay publishes it. */
@Repository
@ConditionalOnProperty(name = "app.events.security-alerts.enabled", havingValue = "true")
public class OutboxSecurityAlertPublisher implements SecurityAlertPublisher {

    private final JdbcTemplate jdbcTemplate;

    public OutboxSecurityAlertPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void cardLocked(String customerId) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (id, event_id, event_type, schema_version, customer_id, alert_type, occurred_at)
                VALUES (?, ?, 'SecurityAlertRaised', 1, ?, 'CARD_LOCKED', ?)
                """,
                eventId, eventId.toString(), UUID.fromString(customerId), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
