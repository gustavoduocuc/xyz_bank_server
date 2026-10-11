package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.dailytransactions.domain.AnomalyDetector;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.dailytransactions.domain.Transaction;
import com.xyzbank.migration.shared.domain.SourceLine;
import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TheJdbcDailyReportPublicationIT {

    /*
     * Cases:
     * 1. Copies the staged transactions to the daily reports with their source line
     * 2. Publishing twice leaves the same reports
     * 3. Two staged transactions with the same id keep the one from the lowest line
     */

    @Nested
    class TheJdbcDailyReportPublication {

        private JdbcTemplate jdbcTemplate;
        private JdbcDailyReportWriter writer;
        private JdbcDailyReportPublication publication;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            writer = new JdbcDailyReportWriter(jdbcTemplate);
            publication = new JdbcDailyReportPublication(jdbcTemplate);
        }

        @Test
        void copiesTheStagedTransactionsToTheDailyReportsWithTheirSourceLine() {
            writer.write(List.of(
                    processed("9", "2024-01-07", 3000, "debito", 10),
                    processed("2", "2024-01-02", 1500, "credito", 3)));

            publication.publish();

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM daily_transaction_reports WHERE transaction_id = '9'");
            assertEquals("DEBIT", row.get("transaction_type"));
            assertEquals("HIGH_AMOUNT", row.get("anomalies"));
            assertEquals(10, row.get("source_line"));
            assertEquals(2, reports());
        }

        @Test
        void publishingTwiceLeavesTheSameReports() {
            writer.write(List.of(processed("1", "2024-01-01", 1000, "debito", 2)));

            publication.publish();
            publication.publish();

            assertEquals(1, reports());
        }

        @Test
        void twoStagedTransactionsWithTheSameIdKeepTheOneFromTheLowestLine() {
            writer.write(List.of(
                    processed("7", "2024-02-01", 500, "debito", 30),
                    processed("7", "2024-02-02", 900, "credito", 8)));

            publication.publish();

            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM daily_transaction_reports");
            assertEquals("CREDIT", row.get("transaction_type"));
            assertEquals(8, row.get("source_line"));
            assertEquals(1, reports());
        }

        private ProcessedTransaction processed(String id, String date, double amount, String type, int line) {
            return new AnomalyDetector().evaluate(Transaction.create(id, date, amount, type, SourceLine.of(line)));
        }

        private int reports() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_transaction_reports", Integer.class);
            return rows == null ? 0 : rows;
        }
    }
}
