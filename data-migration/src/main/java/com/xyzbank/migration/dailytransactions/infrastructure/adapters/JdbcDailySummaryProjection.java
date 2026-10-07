package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.application.ports.DailySummaryProjection;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDailySummaryProjection implements DailySummaryProjection {

    private static final String rebuild = """
            INSERT INTO daily_transaction_summaries
                (summary_date, total_debits, total_credits, transaction_count, anomaly_count)
            SELECT * FROM (
                SELECT transaction_date AS summary_date,
                       COALESCE(SUM(CASE WHEN transaction_type = 'DEBIT' THEN amount END), 0) AS total_debits,
                       COALESCE(SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount END), 0) AS total_credits,
                       COUNT(*) AS transaction_count,
                       SUM(CASE WHEN anomalies <> '' THEN 1 ELSE 0 END) AS anomaly_count
                FROM daily_transaction_reports
                GROUP BY transaction_date
            ) AS summary
            ON DUPLICATE KEY UPDATE
                total_debits = summary.total_debits,
                total_credits = summary.total_credits,
                transaction_count = summary.transaction_count,
                anomaly_count = summary.anomaly_count
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailySummaryProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void rebuild() {
        jdbcTemplate.update(rebuild);
    }
}
