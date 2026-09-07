package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.InMemoryDailyReportWriter;
import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationGuardTasklet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBatchTest
@SpringBootTest
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "migration.data.daily-transactions=file:data/semana_2/transacciones.csv"
})
class TheDailyTransactionsJobTest {

    /*
     * Cases:
     * 1. Writes valid transactions and omits invalid ones
     * 2. Omits processing when migration already succeeded
     */

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("dailyTransactionsJob")
    private Job dailyTransactionsJob;

    @Autowired
    private InMemoryDailyReportWriter dailyReportWriter;

    @Autowired
    private InMemoryMigrationExecutionPort migrationExecutionPort;

    @BeforeEach
    void resetMigrationLedger() {
        migrationExecutionPort.clear();
    }

    @Test
    void writesValidTransactionsAndOmitsInvalidOnes() throws Exception {
        jobLauncherTestUtils.setJob(Objects.requireNonNull(dailyTransactionsJob));

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(6, dailyReportWriter.written().size());
        assertTrue(dailyReportWriter.written().stream().anyMatch(transaction -> transaction.hasAnomaly()));
        assertTrue(migrationExecutionPort.hasSuccessfulExecution("dailyTransactionsJob"));
    }

    @Test
    void omitsProcessingWhenMigrationAlreadySucceeded() throws Exception {
        migrationExecutionPort.markSuccess("dailyTransactionsJob", 6, 4);
        jobLauncherTestUtils.setJob(Objects.requireNonNull(dailyTransactionsJob));
        int writtenBefore = dailyReportWriter.written().size();

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("run.id", System.currentTimeMillis() + 1)
                        .toJobParameters()
        );

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(writtenBefore, dailyReportWriter.written().size());
        assertTrue(execution.getStepExecutions().stream().anyMatch(step ->
                MigrationGuardTasklet.alreadyMigratedExitCode.equals(step.getExitStatus().getExitCode())));
    }

    @TestConfiguration
    static class TestWriters {

        @Bean
        @Primary
        InMemoryDailyReportWriter dailyReportWriter() {
            return new InMemoryDailyReportWriter();
        }

        @Bean
        @Primary
        InMemoryMigrationExecutionPort migrationExecutionPort() {
            return new InMemoryMigrationExecutionPort();
        }
    }
}
