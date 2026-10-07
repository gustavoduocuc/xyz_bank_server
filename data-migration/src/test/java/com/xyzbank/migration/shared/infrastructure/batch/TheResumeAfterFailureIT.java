package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualMovementStore;
import com.xyzbank.migration.annualreports.infrastructure.adapters.JdbcAnnualMovementStore;
import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.infrastructure.adapters.JdbcDailyReportWriter;
import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.infrastructure.adapters.JdbcAccountBalanceWriter;
import com.xyzbank.migration.shared.infrastructure.support.GoldenSnapshot;
import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "migration.batch.chunk-size=50",
        "migration.batch.throttle-limit=4",
        "migration.batch.skip-limit=2000",
        "migration.data.daily-transactions=file:data/semana_3/transacciones.csv",
        "migration.data.monthly-interests=file:data/semana_3/intereses.csv",
        "migration.data.annual-accounts=file:data/semana_3/cuentas_anuales.csv"
})
@DisplayName("A migration interrupted by a database failure")
class TheResumeAfterFailureIT extends MySqlContainerSupport {

    /*
     * Cases (for each of the three jobs):
     * 1. Fails partway, is restarted on the same instance and completes
     * 2. The restart reads fewer rows than the file holds, because committed chunks are not read again
     * 3. Ends with the same tables as a clean run
     */

    private static final int dataRows = 1000;
    private static final int chunksBeforeFailure = 3;

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private DatabaseFailure databaseFailure;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPreviousRuns() {
        clearMigrationData(jdbcTemplate);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"dailyTransactionsJob", "monthlyInterestsJob", "annualGenerationJob"})
    @DisplayName("resumes from its last committed chunks and converges to the clean result")
    void resumesFromItsLastCommittedChunksAndConvergesToTheCleanResult(String jobName) {
        databaseFailure.armFor(jobName, chunksBeforeFailure);

        int exitCode = runAllMigrationsRunner.runJobs();

        assertEquals(0, exitCode);
        assertTrue(databaseFailure.fired(), "the simulated failure must have happened");
        List<JobExecution> executions = executionsOf(jobName);
        assertEquals(List.of(BatchStatus.FAILED, BatchStatus.COMPLETED),
                executions.stream().map(JobExecution::getStatus).toList());
        assertTrue(workerWrites(executions.get(0)) > 0, "the failed attempt committed some chunks");
        assertTrue(workerReads(executions.get(1)) < dataRows, "the restart must not read committed chunks again");
        assertTablesMatchTheCleanRun();
    }

    private void assertTablesMatchTheCleanRun() {
        assertEquals(GoldenSnapshot.expected("semana_3", "counts"), GoldenSnapshot.of(jdbcTemplate, GoldenSnapshot.counts));
        assertEquals(GoldenSnapshot.expected("semana_3", "daily_transaction_reports"),
                GoldenSnapshot.of(jdbcTemplate, GoldenSnapshot.dailyTransactionReports));
        assertEquals(GoldenSnapshot.expected("semana_3", "daily_transaction_summaries"),
                GoldenSnapshot.of(jdbcTemplate, GoldenSnapshot.dailyTransactionSummaries));
        assertEquals(GoldenSnapshot.expected("semana_3", "account_balances"),
                GoldenSnapshot.of(jdbcTemplate, GoldenSnapshot.accountBalances));
        assertEquals(GoldenSnapshot.expected("semana_3", "annual_audit_reports"),
                GoldenSnapshot.of(jdbcTemplate, GoldenSnapshot.annualAuditReports));
    }

    private List<JobExecution> executionsOf(String jobName) {
        JobInstance instance = jobExplorer.getLastJobInstance(jobName);
        assertNotNull(instance, jobName + " never ran");
        return jobExplorer.getJobExecutions(instance).stream()
                .sorted(Comparator.comparing(JobExecution::getId))
                .toList();
    }

    private long workerWrites(JobExecution execution) {
        return workers(execution).mapToLong(StepExecution::getWriteCount).sum();
    }

    private long workerReads(JobExecution execution) {
        return workers(execution).mapToLong(StepExecution::getReadCount).sum();
    }

    private Stream<StepExecution> workers(JobExecution execution) {
        return execution.getStepExecutions().stream().filter(step -> step.getStepName().contains("Worker:"));
    }

    /**
     * Lets the armed job write {@code afterChunks} chunks, then fails the next write once,
     * after the rows reached the database, so the chunk transaction must roll them back.
     */
    static class DatabaseFailure {

        private final AtomicReference<String> armedJob = new AtomicReference<>();
        private final AtomicInteger chunksLeft = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        void armFor(String jobName, int afterChunks) {
            chunksLeft.set(afterChunks);
            failures.set(0);
            armedJob.set(jobName);
        }

        boolean fired() {
            return failures.get() > 0;
        }

        void afterChunkWritten(String jobName) {
            if (!jobName.equals(armedJob.get())) {
                return;
            }
            if (chunksLeft.getAndDecrement() <= 0 && jobName.equals(armedJob.getAndSet(null))) {
                failures.incrementAndGet();
                throw new DataAccessResourceFailureException("simulated database failure in " + jobName);
            }
        }
    }

    @TestConfiguration
    static class FailingWriters {

        @Bean
        DatabaseFailure databaseFailure() {
            return new DatabaseFailure();
        }

        @Bean
        @Primary
        DailyReportWriter failingDailyReportWriter(JdbcTemplate jdbcTemplate, DatabaseFailure databaseFailure) {
            DailyReportWriter delegate = new JdbcDailyReportWriter(jdbcTemplate);
            return transactions -> {
                delegate.write(transactions);
                databaseFailure.afterChunkWritten("dailyTransactionsJob");
            };
        }

        @Bean
        @Primary
        AccountBalanceWriter failingAccountBalanceWriter(JdbcTemplate jdbcTemplate, DatabaseFailure databaseFailure) {
            AccountBalanceWriter delegate = new JdbcAccountBalanceWriter(jdbcTemplate);
            return balances -> {
                delegate.write(balances);
                databaseFailure.afterChunkWritten("monthlyInterestsJob");
            };
        }

        @Bean
        @Primary
        AnnualMovementStore failingAnnualMovementStore(JdbcTemplate jdbcTemplate, DatabaseFailure databaseFailure) {
            AnnualMovementStore delegate = new JdbcAnnualMovementStore(jdbcTemplate);
            return movements -> {
                delegate.write(movements);
                databaseFailure.afterChunkWritten("annualGenerationJob");
            };
        }
    }
}
