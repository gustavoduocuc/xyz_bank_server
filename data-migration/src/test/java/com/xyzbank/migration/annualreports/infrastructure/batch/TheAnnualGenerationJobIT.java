package com.xyzbank.migration.annualreports.infrastructure.batch;

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
        "migration.data.annual-accounts=classpath:fixtures/annual-partitioned.csv"
})
@DisplayName("The annual generation job")
class TheAnnualGenerationJobIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Stages movements per range and then consolidates them
     * 2. Consolidates an account whose movements span partitions
     */

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("annualGenerationJob")
    private Job annualGenerationJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearMigrationData(jdbcTemplate);
    }

    @Test
    @DisplayName("stages movements per range and then consolidates them")
    void stagesMovementsPerRangeAndThenConsolidatesThem() throws Exception {
        JobExecution execution = launch();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        List<String> steps = execution.getStepExecutions().stream().map(StepExecution::getStepName).sorted().toList();
        assertEquals(List.of(
                "checkAnnualMigrationNotDone",
                "consolidateAnnualAudit",
                "stageAnnualMovementsManager",
                "stageAnnualMovementsWorker:range0",
                "stageAnnualMovementsWorker:range1"), steps);
    }

    @Test
    @DisplayName("consolidates an account whose movements span partitions")
    void consolidatesAnAccountWhoseMovementsSpanPartitions() throws Exception {
        launch();

        Map<String, Object> account = jdbcTemplate.queryForMap("SELECT * FROM annual_audit_reports WHERE account_id = '101'");
        assertEquals(new BigDecimal("1250.00"), account.get("total_deposits"));
        assertEquals(new BigDecimal("500.00"), account.get("total_withdrawals"));
        assertEquals(new BigDecimal("750.00"), account.get("net_balance"));
        assertEquals(3, account.get("movement_count"));
    }

    private JobExecution launch() throws Exception {
        return jobLauncher.run(
                annualGenerationJob, new JobParametersBuilder().addLong("run.id", System.nanoTime()).toJobParameters());
    }
}
