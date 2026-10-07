package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "migration.batch.throttle-limit=2",
        "migration.data.daily-transactions=classpath:fixtures/daily-partitioned.csv"
})
@DisplayName("The daily transactions job")
class TheDailyTransactionsJobIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Processes the file in one worker step per range under a manager step
     * 2. Publishes the reports and their per-date summary
     * 3. Does not publish a business-key duplicate read by another partition
     * 4. Reprocessing the same file after deleting the ledger keeps the same rows
     */

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("dailyTransactionsJob")
    private Job dailyTransactionsJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearMigrationData(jdbcTemplate);
    }

    @Test
    @DisplayName("processes the file in one worker step per range under a manager step")
    void processesTheFileInOneWorkerStepPerRangeUnderAManagerStep() throws Exception {
        JobExecution execution = launch();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<String> steps = execution.getStepExecutions().stream().map(StepExecution::getStepName).sorted().toList();
        assertEquals(List.of(
                "checkDailyMigrationNotDone",
                "processDailyTransactionsManager",
                "processDailyTransactionsWorker:range0",
                "processDailyTransactionsWorker:range1",
                "publishDailyTransactions",
                "summarizeDailyTransactions"), steps);
    }

    @Test
    @DisplayName("publishes the reports and their per-date summary")
    void publishesTheReportsAndTheirPerDateSummary() throws Exception {
        launch();

        assertEquals(List.of("1", "2", "3", "4", "7"), publishedIds());
        Map<String, Object> summary = jdbcTemplate.queryForMap(
                "SELECT * FROM daily_transaction_summaries WHERE summary_date = '2024-06-30'");
        assertEquals(new BigDecimal("1000.00"), summary.get("total_debits"));
        assertEquals(new BigDecimal("13500.00"), summary.get("total_credits"));
        assertEquals(3, summary.get("transaction_count"));
        assertEquals(1, summary.get("anomaly_count"));
    }

    @Test
    @DisplayName("does not publish a business-key duplicate read by another partition")
    void doesNotPublishABusinessKeyDuplicateReadByAnotherPartition() throws Exception {
        launch();

        assertFalse(publishedIds().contains("40"));
    }

    @Test
    @DisplayName("reprocessing the same file after deleting the ledger keeps the same rows")
    void reprocessingTheSameFileAfterDeletingTheLedgerKeepsTheSameRows() throws Exception {
        launch();
        jdbcTemplate.update("DELETE FROM migration_executions");

        JobExecution second = launch();

        assertEquals(BatchStatus.COMPLETED, second.getStatus());
        assertEquals(List.of("1", "2", "3", "4", "7"), publishedIds());
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_transaction_summaries", Integer.class));
    }

    private JobExecution launch() throws Exception {
        return jobLauncher.run(
                dailyTransactionsJob, new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters());
    }

    private List<String> publishedIds() {
        return jdbcTemplate.queryForList(
                "SELECT transaction_id FROM daily_transaction_reports ORDER BY CAST(transaction_id AS UNSIGNED)",
                String.class);
    }
}
