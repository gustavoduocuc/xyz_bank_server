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
class TheJdbcDailyReportWriterIT {

    /*
     * Cases:
     * 1. Stages a chunk keyed by the SHA-256 of the business key, with its source line
     * 2. Writing the same chunk twice leaves the same rows
     * 3. The same business key from a lower line replaces the staged row
     * 4. The same business key from a higher line is ignored
     */

    @Nested
    class TheJdbcDailyReportWriter {

        private JdbcTemplate jdbcTemplate;
        private JdbcDailyReportWriter writer;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            writer = new JdbcDailyReportWriter(jdbcTemplate);
        }

        @Test
        void stagesAChunkKeyedByTheSha256OfTheBusinessKeyWithItsSourceLine() {
            ProcessedTransaction highAmount = processed("9", "2024-01-07", 3000, "debito", 10);
            ProcessedTransaction ordinary = processed("2", "2024-01-02", 1500, "credito", 3);

            writer.write(List.of(highAmount, ordinary));

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM daily_transaction_lines WHERE line_key = SHA2(?, 256)",
                    highAmount.transaction().businessKey());
            assertEquals("9", row.get("transaction_id"));
            assertEquals("DEBIT", row.get("transaction_type"));
            assertEquals("HIGH_AMOUNT", row.get("anomalies"));
            assertEquals(10, row.get("source_line"));
            assertEquals(2, stagedRows());
        }

        @Test
        void writingTheSameChunkTwiceLeavesTheSameRows() {
            List<ProcessedTransaction> chunk = List.of(
                    processed("1", "2024-01-01", 1000, "debito", 2),
                    processed("2", "2024-01-02", 1500, "credito", 3));

            writer.write(chunk);
            writer.write(chunk);

            assertEquals(2, stagedRows());
            assertEquals(List.of("1", "2"), stagedIds());
        }

        @Test
        void theSameBusinessKeyFromALowerLineReplacesTheStagedRow() {
            writer.write(List.of(processed("912", "2024-06-30", 800, "debito", 913)));

            writer.write(List.of(processed("40", "2024-06-30", 800, "debito", 41)));

            assertEquals(List.of("40"), stagedIds());
            assertEquals(41, jdbcTemplate.queryForObject("SELECT source_line FROM daily_transaction_lines", Integer.class));
        }

        @Test
        void theSameBusinessKeyFromAHigherLineIsIgnored() {
            writer.write(List.of(processed("40", "2024-06-30", 800, "debito", 41)));

            writer.write(List.of(processed("912", "2024-06-30", 800, "debito", 913)));

            assertEquals(List.of("40"), stagedIds());
            assertEquals(41, jdbcTemplate.queryForObject("SELECT source_line FROM daily_transaction_lines", Integer.class));
        }

        private ProcessedTransaction processed(String id, String date, double amount, String type, int line) {
            return new AnomalyDetector().evaluate(Transaction.create(id, date, amount, type, SourceLine.of(line)));
        }

        private int stagedRows() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_transaction_lines", Integer.class);
            return rows == null ? 0 : rows;
        }

        private List<String> stagedIds() {
            return jdbcTemplate.queryForList(
                    "SELECT transaction_id FROM daily_transaction_lines ORDER BY source_line", String.class);
        }
    }
}
