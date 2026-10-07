package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TheMigrationLedgerListenerTest {

    /*
     * Cases:
     * 1. Marks success after completed process
     * 2. Leaves ledger unchanged when already migrated
     * 3. Leaves ledger unchanged when success already recorded
     * 4. Marks failed after failed process
     * 5. Sums write and skip counts over the worker steps of every execution of the instance
     * 6. Excludes partition manager steps, which aggregate their workers
     * 7. Replaces a FAILED entry with SUCCESS after a restart completes
     */

    @Nested
    class TheMigrationLedgerListener {

        private final JobExecutionHistory noPreviousExecutions = current -> List.of();

        @Test
        void marksSuccessAfterCompletedProcess() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            MigrationLedgerListener listener = new MigrationLedgerListener(port, noPreviousExecutions);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            StepExecution processStep = new StepExecution("processDailyTransactions", jobExecution);
            processStep.setWriteCount(7);
            processStep.setProcessSkipCount(3);
            jobExecution.addStepExecutions(List.of(processStep));

            listener.afterJob(jobExecution);

            assertTrue(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void leavesLedgerUnchangedWhenAlreadyMigrated() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markSuccess("dailyTransactionsJob", 7, 3);
            MigrationLedgerListener listener = new MigrationLedgerListener(port, noPreviousExecutions);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            StepExecution guardStep = new StepExecution("checkDailyMigrationNotDone", jobExecution);
            guardStep.setExitStatus(new ExitStatus(MigrationGuardTasklet.alreadyMigratedExitCode));
            jobExecution.addStepExecutions(List.of(guardStep));

            listener.afterJob(jobExecution);

            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
        }

        @Test
        void leavesLedgerUnchangedWhenSuccessAlreadyRecorded() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markSuccess("dailyTransactionsJob", 7, 3);
            MigrationLedgerListener listener = new MigrationLedgerListener(port, noPreviousExecutions);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            jobExecution.addStepExecutions(List.of(new StepExecution("processDailyTransactions", jobExecution)));

            listener.afterJob(jobExecution);

            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void marksFailedAfterFailedProcess() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            MigrationLedgerListener listener = new MigrationLedgerListener(port, noPreviousExecutions);
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.FAILED);
            jobExecution.addStepExecutions(List.of(new StepExecution("processDailyTransactions", jobExecution)));

            listener.afterJob(jobExecution);

            assertFalse(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals("FAILED", port.executionFor("dailyTransactionsJob").status());
        }

        @Test
        void sumsWriteAndSkipCountsOverTheWorkerStepsOfEveryExecutionOfTheInstance() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            JobExecution failedAttempt = jobExecution("dailyTransactionsJob", BatchStatus.FAILED, 1L);
            worker(failedAttempt, "processDailyTransactionsWorker:range0", 4, 1);
            JobExecution restart = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED, 2L);
            worker(restart, "processDailyTransactionsWorker:range0", 3, 2);
            MigrationLedgerListener listener = new MigrationLedgerListener(port, current -> List.of(failedAttempt));

            listener.afterJob(restart);

            assertEquals(7, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(3, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void excludesPartitionManagerStepsWhichAggregateTheirWorkers() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            JobExecution jobExecution = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED);
            worker(jobExecution, "processDailyTransactionsWorker:range0", 5, 1);
            worker(jobExecution, "processDailyTransactionsWorker:range1", 5, 1);
            worker(jobExecution, "processDailyTransactionsManager", 10, 2);

            new MigrationLedgerListener(port, noPreviousExecutions).afterJob(jobExecution);

            assertEquals(10, port.executionFor("dailyTransactionsJob").writeCount());
            assertEquals(2, port.executionFor("dailyTransactionsJob").skipCount());
        }

        @Test
        void replacesAFailedEntryWithSuccessAfterARestartCompletes() {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markFailed("dailyTransactionsJob");
            JobExecution restart = jobExecution("dailyTransactionsJob", BatchStatus.COMPLETED, 2L);
            worker(restart, "processDailyTransactionsWorker:range0", 3, 0);

            new MigrationLedgerListener(port, noPreviousExecutions).afterJob(restart);

            assertTrue(port.hasSuccessfulExecution("dailyTransactionsJob"));
            assertEquals(3, port.executionFor("dailyTransactionsJob").writeCount());
        }

        private void worker(JobExecution jobExecution, String stepName, long writes, long skips) {
            StepExecution step = new StepExecution(stepName, jobExecution);
            step.setWriteCount(writes);
            step.setProcessSkipCount(skips);
            jobExecution.addStepExecutions(List.of(step));
        }

        private JobExecution jobExecution(String jobName, BatchStatus status) {
            return jobExecution(jobName, status, 1L);
        }

        private JobExecution jobExecution(String jobName, BatchStatus status, long executionId) {
            JobInstance jobInstance = new JobInstance(1L, jobName);
            JobExecution jobExecution = new JobExecution(jobInstance, executionId, null);
            jobExecution.setStatus(status);
            return jobExecution;
        }
    }
}
