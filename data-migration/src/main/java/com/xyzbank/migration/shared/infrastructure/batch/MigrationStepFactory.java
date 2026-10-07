package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds the steps every migration job shares: restartable fault-tolerant chunk workers,
 * the partition manager that runs them in parallel, and single-shot tasklets. Every step
 * may start at most {@code max-restarts + 1} times.
 */
public class MigrationStepFactory {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TaskExecutor batchTaskExecutor;
    private final MigrationBatchSettings settings;
    private final DomainSkipPolicy domainSkipPolicy;
    private final TransientDataAccessRetryPolicy retryPolicy;
    private final BackOffPolicy backOffPolicy;
    private final LoggingRetryListener loggingRetryListener;
    private final StepMetricsListener stepMetricsListener;
    private final ChunkThroughputListener chunkThroughputListener;

    public MigrationStepFactory(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TaskExecutor batchTaskExecutor,
            MigrationBatchSettings settings,
            DomainSkipPolicy domainSkipPolicy,
            TransientDataAccessRetryPolicy retryPolicy,
            BackOffPolicy backOffPolicy,
            LoggingRetryListener loggingRetryListener,
            StepMetricsListener stepMetricsListener,
            ChunkThroughputListener chunkThroughputListener
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.batchTaskExecutor = batchTaskExecutor;
        this.settings = settings;
        this.domainSkipPolicy = domainSkipPolicy;
        this.retryPolicy = retryPolicy;
        this.backOffPolicy = backOffPolicy;
        this.loggingRetryListener = loggingRetryListener;
        this.stepMetricsListener = stepMetricsListener;
        this.chunkThroughputListener = chunkThroughputListener;
    }

    public <I, O> Step worker(
            String name,
            ItemReader<I> reader,
            ItemProcessor<I, O> processor,
            ItemWriter<O> writer
    ) {
        return new StepBuilder(name, jobRepository)
                .<I, O>chunk(settings.chunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .skipPolicy(domainSkipPolicy)
                .retryPolicy(retryPolicy)
                .backOffPolicy(backOffPolicy)
                .listener(loggingRetryListener)
                .listener(new LoggingSkipListener<I, O>())
                .listener(stepMetricsListener)
                .listener(chunkThroughputListener)
                .startLimit(settings.stepStartLimit())
                .build();
    }

    public Step manager(String name, Step worker, Partitioner partitioner) {
        return new StepBuilder(name, jobRepository)
                .partitioner(worker.getName(), partitioner)
                .step(worker)
                .gridSize(settings.throttleLimit())
                .taskExecutor(batchTaskExecutor)
                .startLimit(settings.stepStartLimit())
                .build();
    }

    public Step tasklet(String name, Tasklet tasklet) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(stepMetricsListener)
                .startLimit(settings.stepStartLimit())
                .build();
    }
}
