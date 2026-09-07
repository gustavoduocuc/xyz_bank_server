package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.*;

class TheStepMetricsListenerTest {

    /*
     * Cases:
     * 1. After step returns original exit status
     */

    @Nested
    class TheStepMetricsListener {

        @Test
        void afterStepReturnsOriginalExitStatus() {
            StepMetricsListener listener = new StepMetricsListener();
            JobExecution jobExecution = new JobExecution(new JobInstance(1L, "dailyTransactionsJob"), 1L, null);
            StepExecution stepExecution = new StepExecution("processDailyTransactions", jobExecution);
            stepExecution.setId(10L);
            stepExecution.setStatus(BatchStatus.COMPLETED);
            stepExecution.setExitStatus(ExitStatus.COMPLETED);
            stepExecution.setReadCount(10);
            stepExecution.setWriteCount(7);

            listener.beforeStep(stepExecution);
            ExitStatus exitStatus = listener.afterStep(stepExecution);

            assertEquals(ExitStatus.COMPLETED, exitStatus);
        }
    }
}
