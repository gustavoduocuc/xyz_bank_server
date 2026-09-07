package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.LocalDateTime;

public class JobSummaryListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(JobSummaryListener.class);

    private final int chunkSize;
    private final int throttleLimit;

    public JobSummaryListener(int chunkSize, int throttleLimit) {
        this.chunkSize = chunkSize;
        this.throttleLimit = throttleLimit;
    }

    @Override
    public void beforeJob(@NonNull JobExecution jobExecution) {
        logger.info(
                "Starting job={} chunkSize={} throttleLimit={}",
                jobExecution.getJobInstance().getJobName(),
                chunkSize,
                throttleLimit
        );
    }

    @Override
    public void afterJob(@NonNull JobExecution jobExecution) {
        long durationMs = durationMillis(jobExecution);
        logger.info(
                "Finished job={} status={} durationMs={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                durationMs
        );
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            logger.info(
                    "Step summary name={} read={} write={} skip={} filter={} commit={}",
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getCommitCount()
            );
        }
    }

    private long durationMillis(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        if (start == null || end == null) {
            return 0;
        }
        return Duration.between(start, end).toMillis();
    }
}
