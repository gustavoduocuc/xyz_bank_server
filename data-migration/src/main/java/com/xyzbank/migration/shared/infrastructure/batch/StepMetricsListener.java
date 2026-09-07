package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StepMetricsListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(StepMetricsListener.class);

    private final ConcurrentMap<Long, Instant> startedAtByExecution = new ConcurrentHashMap<>();

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        startedAtByExecution.put(stepExecution.getId(), Instant.now());
        logger.info("Step starting name={}", stepExecution.getStepName());
    }

    @Override
    @Nullable
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        Instant startedAt = startedAtByExecution.remove(stepExecution.getId());
        long durationMs = startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).toMillis();
        double seconds = Math.max(durationMs / 1000.0, 0.001);
        double throughput = stepExecution.getWriteCount() / seconds;

        logger.info(
                "Step metrics name={} status={} durationMs={} read={} write={} skip={} throughputPerSec={}",
                stepExecution.getStepName(),
                stepExecution.getStatus(),
                durationMs,
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                String.format("%.2f", throughput)
        );
        return stepExecution.getExitStatus();
    }
}
