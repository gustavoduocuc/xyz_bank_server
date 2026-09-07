package com.xyzbank.migration.shared.application.ports;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheInMemoryMigrationExecutionPortTest {

    /*
     * Cases:
     * 1. Does not report success before marking
     * 2. Reports success after marking
     * 3. Does not treat failed execution as success
     */

    @Nested
    class TheInMemoryMigrationExecutionPort {

        @Test
        void doesNotReportSuccessBeforeMarking() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();

            assertFalse(port.hasSuccessfulExecution("dailyTransactionsJob"));
        }

        @Test
        void reportsSuccessAfterMarking() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();

            port.markSuccess("dailyTransactionsJob", 7, 3);

            assertTrue(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals("SUCCESS", port.executionFor("dailyTransactionsJob").status());
            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void doesNotTreatFailedExecutionAsSuccess() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();

            port.markFailed("dailyTransactionsJob");

            assertFalse(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals("FAILED", port.executionFor("dailyTransactionsJob").status());
        }
    }
}
