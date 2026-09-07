package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.domain.AnomalyType;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

public class JdbcDailyReportWriter implements DailyReportWriter {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyReportWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<ProcessedTransaction> transactions) {
        for (ProcessedTransaction processed : transactions) {
            jdbcTemplate.update(
                    """
                            INSERT INTO daily_transaction_reports
                                (transaction_id, transaction_date, amount, transaction_type, anomalies)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    processed.idValue(),
                    Date.valueOf(processed.dateAsIso()),
                    processed.amountValue(),
                    processed.type().name(),
                    anomaliesAsText(processed)
            );
        }
    }

    private String anomaliesAsText(ProcessedTransaction processed) {
        if (processed.anomalies().isEmpty()) {
            return "";
        }
        return processed.anomalies().stream()
                .map(AnomalyType::name)
                .collect(Collectors.joining(","));
    }
}
