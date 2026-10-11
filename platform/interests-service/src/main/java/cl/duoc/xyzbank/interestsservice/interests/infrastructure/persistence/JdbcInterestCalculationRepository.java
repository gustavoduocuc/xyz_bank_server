package cl.duoc.xyzbank.interestsservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

public class JdbcInterestCalculationRepository implements InterestCalculationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInterestCalculationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(InterestCalculation calculation) {
        jdbcTemplate.update(
                """
                INSERT INTO interests.interest_calculations (event_id, account_id, period, amount, currency, status, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO UPDATE SET status = EXCLUDED.status, reason = EXCLUDED.reason
                """,
                calculation.eventId(),
                calculation.accountId(),
                calculation.period(),
                calculation.amount(),
                calculation.currency(),
                calculation.status().name(),
                calculation.reason());
    }

    @Override
    public Optional<InterestCalculation> findByEventId(String eventId) {
        return jdbcTemplate.query(
                        "SELECT event_id, account_id, period, amount, currency, status, reason "
                                + "FROM interests.interest_calculations WHERE event_id = ?",
                        (row, rowNumber) -> InterestCalculation.restore(
                                row.getString("event_id"),
                                row.getString("account_id"),
                                row.getInt("period"),
                                row.getBigDecimal("amount"),
                                row.getString("currency"),
                                InterestCalculationStatus.valueOf(row.getString("status")),
                                row.getString("reason")),
                        eventId)
                .stream()
                .findFirst();
    }
}
