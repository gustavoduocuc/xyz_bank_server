package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.domain.AnomalyDetector;
import com.xyzbank.migration.dailytransactions.domain.AnomalyType;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.shared.domain.DomainError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheDailyTransactionProcessorTest {

    /*
     * Cases:
     * 1. Accepts valid transaction
     * 2. Does not allow non-positive amount
     * 3. Does not allow duplicate transaction
     * 4. Marks high amount anomaly and keeps transaction
     */

    @Nested
    class TheDailyTransactionProcessor {

        private DailyTransactionProcessor processor;

        @BeforeEach
        void setUp() {
            processor = new DailyTransactionProcessor(new AnomalyDetector());
        }

        @Test
        void acceptsValidTransaction() {
            DailyTransactionLine line = line("1", "2024-01-01", 1000.0, "debito");

            ProcessedTransaction processed = processor.process(line);

            assertFalse(processed.hasAnomaly());
            assertEquals("1", processed.idValue());
        }

        @Test
        void doesNotAllowNonPositiveAmount() {
            DailyTransactionLine line = line("3", "2024-01-03", -200.0, "debito");

            assertThrows(DomainError.class, () -> processor.process(line));
        }

        @Test
        void doesNotAllowDuplicateTransaction() {
            processor.process(line("6", "2024-01-05", 700.0, "debito"));

            assertThrows(DomainError.class, () -> processor.process(line("8", "2024-01-05", 700.0, "debito")));
        }

        @Test
        void marksHighAmountAnomalyAndKeepsTransaction() {
            ProcessedTransaction processed = processor.process(line("9", "2024-01-07", 3000.0, "debito"));

            assertTrue(processed.anomalies().contains(AnomalyType.HIGH_AMOUNT));
            assertFalse(processed.isDuplicate());
        }

        @Test
        void acceptsPaddedTextFields() {
            ProcessedTransaction processed = processor.process(line(" 1 ", " 2024-01-01 ", 1000.0, " debito "));

            assertEquals("1", processed.idValue());
        }

        private DailyTransactionLine line(String id, String fecha, Double monto, String tipo) {
            return new DailyTransactionLine(id, fecha, monto, tipo);
        }
    }
}
