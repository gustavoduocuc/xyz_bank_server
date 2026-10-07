package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Launches a migration job so that a failure is resumed instead of started over: a
 * FAILED or STOPPED execution of the same input is restarted from its last committed
 * chunk, an execution orphaned by a crashed process is marked FAILED first, and the
 * job is restarted at most {@code maxRestarts} times, counted across processes.
 */
public class JobRestartLauncher {

    public static final String inputFileParameter = "input.file";

    private static final Logger logger = LoggerFactory.getLogger(JobRestartLauncher.class);

    private final JobLauncher jobLauncher;
    private final JobOperator jobOperator;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final int maxRestarts;

    public JobRestartLauncher(
            JobLauncher jobLauncher,
            JobOperator jobOperator,
            JobExplorer jobExplorer,
            JobRepository jobRepository,
            int maxRestarts
    ) {
        this.jobLauncher = jobLauncher;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.maxRestarts = maxRestarts;
    }

    public JobExecution launch(Job job, String inputFile) throws Exception {
        JobExecution execution = lastExecutionFor(job.getName(), inputFile);
        if (execution != null && execution.isRunning()) {
            markOrphanedAsFailed(execution);
        }
        if (execution == null || !isRestartable(execution)) {
            execution = jobLauncher.run(job, nextParameters(job, inputFile));
        }
        while (isRestartable(execution)) {
            execution = restart(execution);
        }
        return execution;
    }

    private JobExecution lastExecutionFor(String jobName, String inputFile) {
        JobInstance instance = jobExplorer.getLastJobInstance(jobName);
        if (instance == null) {
            return null;
        }
        JobExecution last = jobExplorer.getLastJobExecution(instance);
        if (last == null || !Objects.equals(inputFile, last.getJobParameters().getString(inputFileParameter))) {
            return null;
        }
        return last;
    }

    private boolean isRestartable(JobExecution execution) {
        return execution.getStatus() == BatchStatus.FAILED || execution.getStatus() == BatchStatus.STOPPED;
    }

    private JobExecution restart(JobExecution failed) throws Exception {
        String jobName = failed.getJobInstance().getJobName();
        int restarts = jobExplorer.getJobExecutions(failed.getJobInstance()).size() - 1;
        if (restarts >= maxRestarts) {
            throw new MigrationRestartLimitExceeded(jobName, maxRestarts);
        }
        logger.warn("Restarting {} execution={} status={} restart={}/{}",
                jobName, failed.getId(), failed.getStatus(), restarts + 1, maxRestarts);
        Long restartedId = jobOperator.restart(failed.getId());
        return jobExplorer.getJobExecution(restartedId);
    }

    private JobParameters nextParameters(Job job, String inputFile) {
        return new JobParametersBuilder(jobExplorer)
                .getNextJobParameters(job)
                .addString(inputFileParameter, inputFile)
                .toJobParameters();
    }

    /**
     * This process is the only launcher, so an execution still marked as running was
     * left behind by a crash. Its chunks were committed with their reader state, which
     * makes FAILED (restartable) the right status rather than ABANDONED.
     */
    private void markOrphanedAsFailed(JobExecution orphan) {
        logger.warn("Marking orphaned execution={} of {} as FAILED", orphan.getId(), orphan.getJobInstance().getJobName());
        LocalDateTime now = LocalDateTime.now();
        for (StepExecution step : orphan.getStepExecutions()) {
            if (step.getStatus().isRunning()) {
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(ExitStatus.FAILED);
                step.setEndTime(now);
                jobRepository.update(step);
            }
        }
        orphan.setStatus(BatchStatus.FAILED);
        orphan.setExitStatus(ExitStatus.FAILED);
        orphan.setEndTime(now);
        jobRepository.update(orphan);
    }
}
