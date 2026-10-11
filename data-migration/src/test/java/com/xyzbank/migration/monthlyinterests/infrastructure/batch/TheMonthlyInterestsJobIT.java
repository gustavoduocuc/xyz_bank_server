package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

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
        "migration.data.monthly-interests=classpath:fixtures/monthly-partitioned.csv"
})
@DisplayName("The monthly interests job")
class TheMonthlyInterestsJobIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Applies interests in one worker step per range under a manager step
     * 2. Keeps the account from the lowest line when a duplicate falls in another partition
     */

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("monthlyInterestsJob")
    private Job monthlyInterestsJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearMigrationData(jdbcTemplate);
    }

    @Test
    @DisplayName("applies interests in one worker step per range under a manager step")
    void appliesInterestsInOneWorkerStepPerRangeUnderAManagerStep() throws Exception {
        JobExecution execution = launch();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<String> steps = execution.getStepExecutions().stream().map(StepExecution::getStepName).sorted().toList();
        assertEquals(List.of(
                "calculateMonthlyInterestsManager",
                "calculateMonthlyInterestsWorker:range0",
                "calculateMonthlyInterestsWorker:range1",
                "checkMonthlyMigrationNotDone"), steps);
        assertEquals(new BigDecimal("0.0150"), jdbcTemplate.queryForObject(
                "SELECT interest_rate FROM account_balances WHERE account_id = '102'", BigDecimal.class));
    }

    @Test
    @DisplayName("keeps the account from the lowest line when a duplicate falls in another partition")
    void keepsTheAccountFromTheLowestLineWhenADuplicateFallsInAnotherPartition() throws Exception {
        launch();

        Map<String, Object> account = jdbcTemplate.queryForMap("SELECT * FROM account_balances WHERE account_id = '137'");
        assertEquals(new BigDecimal("7000.00"), account.get("previous_balance"));
        assertEquals(2, account.get("source_line"));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_balances", Integer.class));
    }

    private JobExecution launch() throws Exception {
        return jobLauncher.run(
                monthlyInterestsJob, new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters());
    }
}
