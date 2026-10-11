package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The run-all migrations runner")
class TheRunAllMigrationsRunnerIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Launches the three jobs in order against MySQL and a CSV fixture, exiting with code 0
     * 2. A second run against an already-migrated database adds no rows and exits with code 0
     */

    @DynamicPropertySource
    static void registerMigrationProperties(DynamicPropertyRegistry registry) {
        registry.add("migration.run-all", () -> "false");
        registry.add("spring.batch.job.enabled", () -> "false");
        registry.add("migration.batch.skip-limit", () -> "2000");
        registry.add("migration.data.daily-transactions", () -> "file:data/semana_3/transacciones.csv");
        registry.add("migration.data.monthly-interests", () -> "file:data/semana_3/intereses.csv");
        registry.add("migration.data.annual-accounts", () -> "file:data/semana_3/cuentas_anuales.csv");
    }

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPreviousRuns() {
        clearMigrationData(jdbcTemplate);
    }

    @Test
    @DisplayName("launches the three jobs in order then finishes successfully")
    void launchesTheThreeJobsInOrderThenFinishesSuccessfully() throws Exception {
        int exitCode = runAllMigrationsRunner.runJobs();

        assertEquals(0, exitCode);
        assertTrue(count("daily_transaction_reports") > 0);
        assertTrue(count("account_balances") > 0);
        assertTrue(count("annual_audit_reports") > 0);
        assertEquals("SUCCESS", statusOf("dailyTransactionsJob"));
        assertEquals("SUCCESS", statusOf("monthlyInterestsJob"));
        assertEquals("SUCCESS", statusOf("annualGenerationJob"));
    }

    @Test
    @DisplayName("adds no rows and exits with code 0 on a second run against an already-migrated database")
    void exitsSuccessfullyOnASecondRunAgainstAnAlreadyMigratedDatabase() throws Exception {
        runAllMigrationsRunner.runJobs();
        int dailyReports = count("daily_transaction_reports");
        int balances = count("account_balances");
        int audits = count("annual_audit_reports");

        int exitCode = runAllMigrationsRunner.runJobs();

        assertEquals(0, exitCode);
        assertEquals(dailyReports, count("daily_transaction_reports"));
        assertEquals(balances, count("account_balances"));
        assertEquals(audits, count("annual_audit_reports"));
        assertEquals("SUCCESS", statusOf("dailyTransactionsJob"));
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private String statusOf(String jobName) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM migration_executions WHERE job_name = ?",
                String.class,
                jobName);
    }
}
