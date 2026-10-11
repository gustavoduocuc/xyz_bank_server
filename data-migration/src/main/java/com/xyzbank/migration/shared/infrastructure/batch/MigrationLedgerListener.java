package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.lang.NonNull;

import java.util.Objects;
import java.util.stream.Stream;

public class MigrationLedgerListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(MigrationLedgerListener.class);

    private final MigrationExecutionPort migrationExecutionPort;
    private final JobExecutionHistory jobExecutionHistory;

    public MigrationLedgerListener(
            MigrationExecutionPort migrationExecutionPort,
            JobExecutionHistory jobExecutionHistory
    ) {
        this.migrationExecutionPort = migrationExecutionPort;
        this.jobExecutionHistory = jobExecutionHistory;
    }

    @Override
    public void afterJob(@NonNull JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();

        if (wasSkippedAsAlreadyMigrated(jobExecution)) {
            logger.info("Job {} ended as already migrated; ledger unchanged", jobName);
            return;
        }

        if (migrationExecutionPort.hasSuccessfulExecution(jobName)) {
            logger.info("Job {} already marked SUCCESS; ledger unchanged", jobName);
            return;
        }

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            int writeCount = totalWriteCount(jobExecution);
            int skipCount = totalSkipCount(jobExecution);
            migrationExecutionPort.markSuccess(jobName, writeCount, skipCount);
            logger.info("Marked migration SUCCESS for {} writeCount={} skipCount={}", jobName, writeCount, skipCount);
            return;
        }

        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            migrationExecutionPort.markFailed(jobName);
            logger.warn("Marked migration FAILED for {}", jobName);
        }
    }

    private boolean wasSkippedAsAlreadyMigrated(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
                .anyMatch(step -> MigrationGuardTasklet.alreadyMigratedExitCode.equals(step.getExitStatus().getExitCode()));
    }

    private int totalWriteCount(JobExecution jobExecution) {
        return (int) countedSteps(jobExecution).mapToLong(StepExecution::getWriteCount).sum();
    }

    private int totalSkipCount(JobExecution jobExecution) {
        return (int) countedSteps(jobExecution).mapToLong(StepExecution::getSkipCount).sum();
    }

    private Stream<StepExecution> countedSteps(JobExecution jobExecution) {
        Stream<JobExecution> previous = jobExecutionHistory.previousExecutions(jobExecution).stream()
                .filter(execution -> !Objects.equals(execution.getId(), jobExecution.getId()));
        return Stream.concat(previous, Stream.of(jobExecution))
                .flatMap(execution -> execution.getStepExecutions().stream())
                .filter(step -> !MigrationStepFactory.isManager(step.getStepName()));
    }
}
