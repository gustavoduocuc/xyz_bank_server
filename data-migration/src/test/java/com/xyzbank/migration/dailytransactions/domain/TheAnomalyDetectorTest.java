package com.xyzbank.migration.dailytransactions.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheAnomalyDetectorTest {

    /*
     * Cases:
     * 1. Marks high amount as anomaly
     * 2. Marks duplicate as anomaly and omittable
     * 3. Accepts first occurrence without anomaly
     * 4. Does not mark normal amount as high-amount anomaly
     */

    @Nested
    class TheAnomalyDetector {

        private AnomalyDetector detector;

        @BeforeEach
        void setUp() {
            detector = new AnomalyDetector();
        }

        @Test
        void marksHighAmountAsAnomaly() {
            Transaction transaction = Transaction.create("9", "2024-01-07", 3000, "debito");

            ProcessedTransaction processed = detector.evaluate(transaction);

            assertTrue(processed.hasAnomaly());
            assertTrue(processed.anomalies().contains(AnomalyType.HIGH_AMOUNT));
            assertFalse(processed.isDuplicate());
        }

        @Test
        void marksDuplicateAsAnomalyAndOmittable() {
            Transaction first = Transaction.create("6", "2024-01-05", 700, "debito");
            Transaction duplicate = Transaction.create("8", "2024-01-05", 700, "debito");

            detector.evaluate(first);
            ProcessedTransaction processed = detector.evaluate(duplicate);

            assertTrue(processed.hasAnomaly());
            assertTrue(processed.anomalies().contains(AnomalyType.DUPLICATE));
            assertTrue(processed.isDuplicate());
        }

        @Test
        void acceptsFirstOccurrenceWithoutAnomaly() {
            Transaction transaction = Transaction.create("1", "2024-01-01", 1000, "debito");

            ProcessedTransaction processed = detector.evaluate(transaction);

            assertFalse(processed.hasAnomaly());
            assertFalse(processed.isDuplicate());
        }

        @Test
        void doesNotMarkNormalAmountAsHighAmountAnomaly() {
            Transaction transaction = Transaction.create("2", "2024-01-02", 1500, "credito");

            ProcessedTransaction processed = detector.evaluate(transaction);

            assertFalse(processed.anomalies().contains(AnomalyType.HIGH_AMOUNT));
        }
    }
}
