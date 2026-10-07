package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.domain.AnomalyType;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.shared.infrastructure.adapters.FirstWinsUpsert;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stages each chunk in {@code daily_transaction_lines}, keyed by the transaction's
 * business key, so a duplicate read by another partition or after a restart collapses
 * to the earliest line. {@link JdbcDailyReportPublication} publishes the staged rows.
 */
public class JdbcDailyReportWriter implements DailyReportWriter {

    private static final String upsert = FirstWinsUpsert.statement(
            "daily_transaction_lines",
            "line_key",
            "SHA2(?, 256)",
            List.of("transaction_id", "transaction_date", "amount", "transaction_type", "anomalies")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcDailyReportWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<ProcessedTransaction> transactions) {
        List<Object[]> rows = transactions.stream()
                .map(processed -> new Object[]{
                        processed.transaction().businessKey(),
                        processed.idValue(),
                        Date.valueOf(processed.dateAsIso()),
                        processed.amountValue(),
                        processed.type().name(),
                        anomaliesAsText(processed),
                        processed.sourceLine().number()
                })
                .toList();
        jdbcTemplate.batchUpdate(upsert, rows);
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
