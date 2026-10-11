package com.xyzbank.migration.dailytransactions.infrastructure.adapters;

import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TheJdbcDailySummaryProjectionIT {

    /*
     * Cases:
     * 1. Sums debits, credits, transactions and anomalies per date
     * 2. A date with only credits has zero debits, not null
     * 3. Rebuilding twice gives the same rows
     */

    @Nested
    class TheJdbcDailySummaryProjection {

        private JdbcTemplate jdbcTemplate;
        private JdbcDailySummaryProjection projection;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            projection = new JdbcDailySummaryProjection(jdbcTemplate);
        }

        @Test
        void sumsDebitsCreditsTransactionsAndAnomaliesPerDate() {
            report("1", "2024-06-30", "1000.00", "DEBIT", "");
            report("2", "2024-06-30", "3000.00", "CREDIT", "");
            report("3", "2024-06-30", "12000.00", "CREDIT", "HIGH_AMOUNT");
            report("4", "2024-07-01", "50.00", "DEBIT", "");

            projection.rebuild();

            Map<String, Object> summary = summaryOf("2024-06-30");
            assertEquals(new BigDecimal("1000.00"), summary.get("total_debits"));
            assertEquals(new BigDecimal("15000.00"), summary.get("total_credits"));
            assertEquals(3, summary.get("transaction_count"));
            assertEquals(1, summary.get("anomaly_count"));
            assertEquals(2, rows());
        }

        @Test
        void aDateWithOnlyCreditsHasZeroDebitsNotNull() {
            report("2", "2024-06-30", "3000.00", "CREDIT", "");

            projection.rebuild();

            assertEquals(new BigDecimal("0.00"), summaryOf("2024-06-30").get("total_debits"));
        }

        @Test
        void rebuildingTwiceGivesTheSameRows() {
            report("1", "2024-06-30", "1000.00", "DEBIT", "");

            projection.rebuild();
            projection.rebuild();

            assertEquals(new BigDecimal("1000.00"), summaryOf("2024-06-30").get("total_debits"));
            assertEquals(1, summaryOf("2024-06-30").get("transaction_count"));
            assertEquals(1, rows());
        }

        private void report(String id, String date, String amount, String type, String anomalies) {
            jdbcTemplate.update(
                    """
                            INSERT INTO daily_transaction_reports
                                (transaction_id, transaction_date, amount, transaction_type, anomalies, source_line)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    id, date, new BigDecimal(amount), type, anomalies, Integer.parseInt(id) + 1);
        }

        private Map<String, Object> summaryOf(String date) {
            return jdbcTemplate.queryForMap("SELECT * FROM daily_transaction_summaries WHERE summary_date = ?", date);
        }

        private int rows() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_transaction_summaries", Integer.class);
            return rows == null ? 0 : rows;
        }
    }
}
