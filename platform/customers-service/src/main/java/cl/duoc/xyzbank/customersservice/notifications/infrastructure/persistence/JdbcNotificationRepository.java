package cl.duoc.xyzbank.customersservice.notifications.infrastructure.persistence;

import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationKind;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveIfAbsent(Notification notification) {
        jdbcTemplate.update(
                """
                INSERT INTO customers.notifications
                    (event_id, customer_id, kind, account_id, type, amount, currency, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                notification.eventId(),
                notification.customerId(),
                notification.kind().name(),
                notification.accountId(),
                notification.type(),
                notification.amount(),
                notification.currency(),
                notification.occurredAt());
    }

    @Override
    public List<Notification> latestOf(String customerId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_id, customer_id, kind, account_id, type, amount, currency, occurred_at
                FROM customers.notifications
                WHERE customer_id = ?
                ORDER BY occurred_at DESC, event_id
                LIMIT ?
                """,
                (row, rowNumber) -> new Notification(
                        row.getString("event_id"),
                        row.getString("customer_id"),
                        NotificationKind.valueOf(row.getString("kind")),
                        row.getString("account_id"),
                        row.getString("type"),
                        row.getBigDecimal("amount"),
                        row.getString("currency"),
                        row.getObject("occurred_at", java.time.OffsetDateTime.class)),
                customerId,
                limit);
    }
}
