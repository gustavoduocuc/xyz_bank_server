package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.domain.AnomalyDetector;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.dailytransactions.infrastructure.adapters.JdbcDailyReportWriter;
import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import com.xyzbank.migration.shared.infrastructure.batch.ChunkThroughputListener;
import com.xyzbank.migration.shared.infrastructure.batch.DomainSkipPolicy;
import com.xyzbank.migration.shared.infrastructure.batch.JobSummaryListener;
import com.xyzbank.migration.shared.infrastructure.batch.LoggingRetryListener;
import com.xyzbank.migration.shared.infrastructure.batch.LoggingSkipListener;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationGuardTasklet;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationLedgerListener;
import com.xyzbank.migration.shared.infrastructure.batch.StepMetricsListener;
import com.xyzbank.migration.shared.infrastructure.batch.TransientDataAccessRetryPolicy;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.batch.repeat.RepeatOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Objects;

@Configuration
public class DailyTransactionsJobConfig {

    @Bean
    @StepScope
    public AnomalyDetector anomalyDetector() {
        return new AnomalyDetector();
    }

    @Bean
    public DailyReportWriter dailyReportWriter(JdbcTemplate jdbcTemplate) {
        return new JdbcDailyReportWriter(jdbcTemplate);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<DailyTransactionLine> dailyTransactionReader(
            @Value("${migration.data.daily-transactions}") Resource resource
    ) {
        return new FlatFileItemReaderBuilder<DailyTransactionLine>()
                .name("dailyTransactionReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(new DailyTransactionLineMapper())
                .build();
    }

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<DailyTransactionLine> synchronizedDailyTransactionReader(
            @NonNull FlatFileItemReader<DailyTransactionLine> dailyTransactionReader
    ) {
        return new SynchronizedItemStreamReaderBuilder<DailyTransactionLine>()
                .delegate(Objects.requireNonNull(dailyTransactionReader))
                .build();
    }

    @Bean
    @StepScope
    public DailyTransactionProcessor dailyTransactionProcessor(AnomalyDetector anomalyDetector) {
        return new DailyTransactionProcessor(anomalyDetector);
    }

    @Bean
    public DailyReportItemWriter dailyReportItemWriter(DailyReportWriter dailyReportWriter) {
        return new DailyReportItemWriter(dailyReportWriter);
    }

    @Bean
    public Step checkDailyMigrationNotDone(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return new StepBuilder("checkDailyMigrationNotDone", Objects.requireNonNull(jobRepository))
                .tasklet(
                        new MigrationGuardTasklet(migrationExecutionPort, "dailyTransactionsJob"),
                        Objects.requireNonNull(transactionManager)
                )
                .build();
    }

    @Bean
    public Step processDailyTransactions(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SynchronizedItemStreamReader<DailyTransactionLine> synchronizedDailyTransactionReader,
            DailyTransactionProcessor dailyTransactionProcessor,
            DailyReportItemWriter dailyReportItemWriter,
            RepeatOperations batchStepOperations,
            @Value("${migration.batch.chunk-size}") int chunkSize,
            DomainSkipPolicy domainSkipPolicy,
            TransientDataAccessRetryPolicy transientDataAccessRetryPolicy,
            BackOffPolicy transientDataAccessBackOffPolicy,
            LoggingRetryListener loggingRetryListener,
            StepMetricsListener stepMetricsListener,
            ChunkThroughputListener chunkThroughputListener
    ) {
        return new StepBuilder("processDailyTransactions", Objects.requireNonNull(jobRepository))
                .<DailyTransactionLine, ProcessedTransaction>chunk(chunkSize, Objects.requireNonNull(transactionManager))
                .reader(Objects.requireNonNull(synchronizedDailyTransactionReader))
                .processor(Objects.requireNonNull(dailyTransactionProcessor))
                .writer(Objects.requireNonNull(dailyReportItemWriter))
                .stepOperations(Objects.requireNonNull(batchStepOperations))
                .faultTolerant()
                .processorNonTransactional()
                .skipPolicy(Objects.requireNonNull(domainSkipPolicy))
                .retryPolicy(Objects.requireNonNull(transientDataAccessRetryPolicy))
                .backOffPolicy(Objects.requireNonNull(transientDataAccessBackOffPolicy))
                .listener(Objects.requireNonNull(loggingRetryListener))
                .listener(new LoggingSkipListener<DailyTransactionLine, ProcessedTransaction>())
                .listener(Objects.requireNonNull(stepMetricsListener))
                .listener(Objects.requireNonNull(chunkThroughputListener))
                .build();
    }

    @Bean
    public Job dailyTransactionsJob(
            JobRepository jobRepository,
            Step checkDailyMigrationNotDone,
            Step processDailyTransactions,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("dailyTransactionsJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkDailyMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkDailyMigrationNotDone).on("*").to(Objects.requireNonNull(processDailyTransactions))
                .end()
                .build();
    }
}
