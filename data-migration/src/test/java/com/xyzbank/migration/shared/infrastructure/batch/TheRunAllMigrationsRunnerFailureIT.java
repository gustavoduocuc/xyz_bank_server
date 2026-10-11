package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "migration.batch.max-restarts=1",
        "migration.data.daily-transactions=classpath:fixtures/daily-partitioned.csv",
        "migration.data.monthly-interests=classpath:fixtures/monthly-partitioned.csv",
        "migration.data.annual-accounts=classpath:fixtures/annual-partitioned.csv"
})
@DisplayName("The run-all migrations runner when a job keeps failing")
class TheRunAllMigrationsRunnerFailureIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Stops the sequence and exits with code 1 once the restart limit is exhausted
     */

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPreviousRuns() {
        clearMigrationData(jdbcTemplate);
    }

    @Test
    @DisplayName("stops the sequence and exits with code 1 once the restart limit is exhausted")
    void stopsTheSequenceAndExitsWithCode1OnceTheRestartLimitIsExhausted() {
        int exitCode = runAllMigrationsRunner.runJobs();

        assertEquals(1, exitCode);
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM migration_executions WHERE job_name = 'dailyTransactionsJob'", String.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_NAME <> 'dailyTransactionsJob'", Integer.class));
    }

    @TestConfiguration
    static class BrokenDatabase {

        @Bean
        @Primary
        DailyReportWriter dailyReportWriter() {
            return transactions -> {
                throw new DataAccessResourceFailureException("simulated database outage");
            };
        }
    }
}
