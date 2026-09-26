package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.interests.domain.repositories.ProcessedInterestEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcProcessedInterestEventRepository implements ProcessedInterestEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedInterestEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findIdempotencyKey(String eventId) {
        List<String> keys = jdbcTemplate.query(
                "SELECT idempotency_key FROM processed_interest_events WHERE event_id = ?",
                (row, rowNumber) -> row.getString("idempotency_key"),
                eventId);
        return keys.stream().findFirst();
    }

    @Override
    @Transactional
    public void register(String eventId, String idempotencyKey) {
        jdbcTemplate.update(
                """
                INSERT INTO processed_interest_events (event_id, idempotency_key)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                idempotencyKey);
    }
}
