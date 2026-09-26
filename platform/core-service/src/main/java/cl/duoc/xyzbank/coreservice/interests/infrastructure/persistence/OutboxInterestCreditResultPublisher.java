package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class OutboxInterestCreditResultPublisher implements InterestCreditResultPublisher {

    private final JdbcTemplate jdbcTemplate;

    public OutboxInterestCreditResultPublisher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void reject(InterestCreditRejected rejection) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, account_id, period, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                rejection.eventId(),
                "InterestCreditRejected",
                1,
                UUID.fromString(rejection.accountId()),
                periodOf(rejection.eventId()),
                rejection.reason());
    }

    private int periodOf(String eventId) {
        int separator = eventId.lastIndexOf(':');
        return Integer.parseInt(eventId.substring(separator + 1));
    }
}
