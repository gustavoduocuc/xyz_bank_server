package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.domain.AnomalyType;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.dailytransactions.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TheJdbcDailyReportWriterTest {

    /*
     * Cases:
     * 1. Persists processed daily transactions
     */

    @Nested
    class TheJdbcDailyReportWriter {

        private JdbcTemplate jdbcTemplate;
        private JdbcDailyReportWriter writer;

        @BeforeEach
        void setUp() {
            DataSource dataSource = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("classpath:db/schema.sql")
                    .build();
            jdbcTemplate = new JdbcTemplate(dataSource);
            writer = new JdbcDailyReportWriter(jdbcTemplate);
        }

        @Test
        void persistsProcessedDailyTransactions() {
            ProcessedTransaction processed = ProcessedTransaction.withAnomalies(
                    Transaction.create("9", "2024-01-07", 3000, "debito"),
                    List.of(AnomalyType.HIGH_AMOUNT)
            );

            writer.write(List.of(processed));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM daily_transaction_reports WHERE transaction_id = '9'",
                    Integer.class
            );
            String anomalies = jdbcTemplate.queryForObject(
                    "SELECT anomalies FROM daily_transaction_reports WHERE transaction_id = '9'",
                    String.class
            );
            assertEquals(1, count);
            assertEquals("HIGH_AMOUNT", anomalies);
        }
    }
}
