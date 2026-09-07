package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.*;

class TheMigrationLedgerListenerTest {

    /*
     * Cases:
     * 1. Marks success after completed process
     * 2. Leaves ledger unchanged when already migrated
     * 3. Leaves ledger unchanged when success already recorded
     * 4. Marks failed after failed process
     */

    @Nested
    class TheMigrationLedgerListener {

        @Test
        void marksSuccessAfterCompletedProcess() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            MigrationLedgerListener listener = new MigrationLedgerListener(port);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            StepExecution processStep = new StepExecution("processDailyTransactions", jobExecution);
            processStep.setWriteCount(7);
            processStep.setProcessSkipCount(3);
            jobExecution.addStepExecutions(java.util.List.of(processStep));

            listener.afterJob(jobExecution);

            assertTrue(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void leavesLedgerUnchangedWhenAlreadyMigrated() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markSuccess("dailyTransactionsJob", 7, 3);
            MigrationLedgerListener listener = new MigrationLedgerListener(port);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            StepExecution guardStep = new StepExecution("checkDailyMigrationNotDone", jobExecution);
            guardStep.setExitStatus(new ExitStatus(MigrationGuardTasklet.alreadyMigratedExitCode));
            jobExecution.addStepExecutions(java.util.List.of(guardStep));

            listener.afterJob(jobExecution);

            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
        }

        @Test
        void leavesLedgerUnchangedWhenSuccessAlreadyRecorded() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markSuccess("dailyTransactionsJob", 7, 3);
            MigrationLedgerListener listener = new MigrationLedgerListener(port);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            jobExecution.addStepExecutions(java.util.List.of(new StepExecution("processDailyTransactions", jobExecution)));

            listener.afterJob(jobExecution);

            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void marksFailedAfterFailedProcess() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            MigrationLedgerListener listener = new MigrationLedgerListener(port);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.FAILED);
            jobExecution.addStepExecutions(java.util.List.of(new StepExecution("processDailyTransactions", jobExecution)));

            listener.afterJob(jobExecution);

            assertFalse(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals("FAILED", port.executionFor("dailyTransactionsJob").status());
        }

        private JobExecution jobExecution(String jobName, BatchStatus status) {
            JobInstance jobInstance = new JobInstance(1L, jobName);
            JobExecution jobExecution = new JobExecution(jobInstance, 1L, null);
            jobExecution.setStatus(status);
            return jobExecution;
        }
    }
}
