package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import com.xyzbank.migration.shared.infrastructure.adapters.JdbcMigrationExecutionAdapter;
import org.springframework.batch.repeat.RepeatOperations;
import org.springframework.batch.repeat.support.TaskExecutorRepeatTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SharedBatchConfig {

    @Bean
    public MigrationExecutionPort migrationExecutionPort(JdbcTemplate jdbcTemplate) {
        return new JdbcMigrationExecutionAdapter(jdbcTemplate);
    }

    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor(@Value("${migration.batch.throttle-limit}") int throttleLimit) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(throttleLimit);
        executor.setMaxPoolSize(throttleLimit);
        executor.setQueueCapacity(throttleLimit);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("batch-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    public RepeatOperations batchStepOperations(TaskExecutor batchTaskExecutor) {
        TaskExecutorRepeatTemplate repeatTemplate = new TaskExecutorRepeatTemplate();
        repeatTemplate.setTaskExecutor(Objects.requireNonNull(batchTaskExecutor));
        return repeatTemplate;
    }

    @Bean
    public DomainSkipPolicy domainSkipPolicy(@Value("${migration.batch.skip-limit}") int skipLimit) {
        return new DomainSkipPolicy(skipLimit);
    }

    @Bean
    public TransientDataAccessRetryPolicy transientDataAccessRetryPolicy(
            @Value("${migration.batch.retry-limit}") int retryLimit
    ) {
        return new TransientDataAccessRetryPolicy(retryLimit);
    }

    @Bean
    public LoggingRetryListener loggingRetryListener() {
        return new LoggingRetryListener();
    }

    @Bean
    public BackOffPolicy transientDataAccessBackOffPolicy() {
        long initialIntervalMs = 1000L;
        double multiplier = 2.0;
        long maxIntervalMs = 10000L;
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialIntervalMs);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxIntervalMs);
        return backOffPolicy;
    }

    @Bean
    public JobSummaryListener jobSummaryListener(
            @Value("${migration.batch.chunk-size}") int chunkSize,
            @Value("${migration.batch.throttle-limit}") int throttleLimit
    ) {
        return new JobSummaryListener(chunkSize, throttleLimit);
    }

    @Bean
    public StepMetricsListener stepMetricsListener() {
        return new StepMetricsListener();
    }

    @Bean
    public ChunkThroughputListener chunkThroughputListener() {
        return new ChunkThroughputListener();
    }

    @Bean
    public MigrationLedgerListener migrationLedgerListener(MigrationExecutionPort migrationExecutionPort) {
        return new MigrationLedgerListener(migrationExecutionPort);
    }
}
