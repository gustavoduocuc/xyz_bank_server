package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

import static org.junit.jupiter.api.Assertions.*;

class TheMigrationGuardTaskletTest {

    /*
     * Cases:
     * 1. Continues when job has not been migrated
     * 2. Marks already migrated when SUCCESS exists
     */

    @Nested
    class TheMigrationGuardTasklet {

        @Test
        void continuesWhenJobHasNotBeenMigrated() throws Exception {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            MigrationGuardTasklet tasklet = new MigrationGuardTasklet(port, "dailyTransactionsJob");
            StepContribution contribution = newContribution();

            RepeatStatus status = tasklet.execute(contribution, newChunkContext());

            assertEquals(RepeatStatus.FINISHED, status);
            assertNotEquals(
                    MigrationGuardTasklet.alreadyMigratedExitCode,
                    contribution.getExitStatus().getExitCode()
            );
        }

        @Test
        void marksAlreadyMigratedWhenSuccessExists() throws Exception {
            InMemoryMigrationExecutionPort port = new InMemoryMigrationExecutionPort();
            port.markSuccess("dailyTransactionsJob", 7, 3);
            MigrationGuardTasklet tasklet = new MigrationGuardTasklet(port, "dailyTransactionsJob");
            StepContribution contribution = newContribution();

            RepeatStatus status = tasklet.execute(contribution, newChunkContext());

            assertEquals(RepeatStatus.FINISHED, status);
            assertEquals(MigrationGuardTasklet.alreadyMigratedExitCode, contribution.getExitStatus().getExitCode());
        }

        private StepContribution newContribution() {
            JobExecution jobExecution = new JobExecution(1L);
            StepExecution stepExecution = new StepExecution("checkMigrationNotDone", jobExecution);
            return new StepContribution(stepExecution);
        }

        private ChunkContext newChunkContext() {
            JobExecution jobExecution = new JobExecution(1L);
            StepExecution stepExecution = new StepExecution("checkMigrationNotDone", jobExecution);
            return new ChunkContext(new StepContext(stepExecution));
        }
    }
}
