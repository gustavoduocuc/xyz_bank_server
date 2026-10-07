package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportPublication;
import com.xyzbank.migration.shared.infrastructure.adapters.FirstWinsUpsert;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcDailyReportPublication implements DailyReportPublication {

    private static final List<String> valueColumns =
            List.of("transaction_date", "amount", "transaction_type", "anomalies");

    private static final String publish = """
            INSERT INTO daily_transaction_reports
                (transaction_id, transaction_date, amount, transaction_type, anomalies, source_line)
            SELECT * FROM (
                SELECT transaction_id, transaction_date, amount, transaction_type, anomalies, source_line
                FROM daily_transaction_lines
            ) AS incoming
            """ + FirstWinsUpsert.onDuplicateKeyUpdate("daily_transaction_reports", valueColumns);

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyReportPublication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void publish() {
        jdbcTemplate.update(publish);
    }
}
