package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.InMemoryAnnualAuditWriter;
import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
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
        "migration.data.annual-accounts=file:data/semana_2/cuentas_anuales.csv"
})
class TheAnnualGenerationJobTest {

    /*
     * Cases:
     * 1. Compiles audit summaries and omits invalid movements
     */

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("annualGenerationJob")
    private Job annualGenerationJob;

    @Autowired
    private InMemoryAnnualAuditWriter annualAuditWriter;

    @Test
    void compilesAuditSummariesAndOmitsInvalidMovements() throws Exception {
        jobLauncherTestUtils.setJob(Objects.requireNonNull(annualGenerationJob));

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(7, annualAuditWriter.written().size());
    }

    @TestConfiguration
    static class TestWriters {

        @Bean
        @Primary
        InMemoryAnnualAuditWriter annualAuditWriter() {
            return new InMemoryAnnualAuditWriter();
        }

        @Bean
        @Primary
        InMemoryMigrationExecutionPort migrationExecutionPort() {
            return new InMemoryMigrationExecutionPort();
        }
    }
}
