package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditWriter;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import com.xyzbank.migration.annualreports.domain.DuplicateMovementDetector;
import com.xyzbank.migration.annualreports.infrastructure.adapters.JdbcAnnualAuditWriter;
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
public class AnnualGenerationJobConfig {

    @Bean
    public AnnualAuditWriter annualAuditWriter(JdbcTemplate jdbcTemplate) {
        return new JdbcAnnualAuditWriter(jdbcTemplate);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<AnnualMovementLine> annualMovementReader(
            @Value("${migration.data.annual-accounts}") Resource resource
    ) {
        return new FlatFileItemReaderBuilder<AnnualMovementLine>()
                .name("annualMovementReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(new AnnualMovementLineMapper())
                .build();
    }

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<AnnualMovementLine> synchronizedAnnualMovementReader(
            @NonNull FlatFileItemReader<AnnualMovementLine> annualMovementReader
    ) {
        return new SynchronizedItemStreamReaderBuilder<AnnualMovementLine>()
                .delegate(Objects.requireNonNull(annualMovementReader))
                .build();
    }

    @Bean
    @StepScope
    public DuplicateMovementDetector duplicateMovementDetector() {
        return new DuplicateMovementDetector();
    }

    @Bean
    @StepScope
    public AnnualMovementProcessor annualMovementProcessor(DuplicateMovementDetector duplicateMovementDetector) {
        return new AnnualMovementProcessor(duplicateMovementDetector);
    }

    @Bean
    @StepScope
    public AnnualAuditItemWriter annualAuditItemWriter(AnnualAuditWriter annualAuditWriter) {
        return new AnnualAuditItemWriter(annualAuditWriter);
    }

    @Bean
    public Step checkAnnualMigrationNotDone(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return new StepBuilder("checkAnnualMigrationNotDone", Objects.requireNonNull(jobRepository))
                .tasklet(
                        new MigrationGuardTasklet(migrationExecutionPort, "annualGenerationJob"),
                        Objects.requireNonNull(transactionManager)
                )
                .build();
    }

    @Bean
    public Step compileAnnualAudit(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SynchronizedItemStreamReader<AnnualMovementLine> synchronizedAnnualMovementReader,
            AnnualMovementProcessor annualMovementProcessor,
            AnnualAuditItemWriter annualAuditItemWriter,
            RepeatOperations batchStepOperations,
            @Value("${migration.batch.chunk-size}") int chunkSize,
            DomainSkipPolicy domainSkipPolicy,
            TransientDataAccessRetryPolicy transientDataAccessRetryPolicy,
            BackOffPolicy transientDataAccessBackOffPolicy,
            LoggingRetryListener loggingRetryListener,
            StepMetricsListener stepMetricsListener,
            ChunkThroughputListener chunkThroughputListener
    ) {
        return new StepBuilder("compileAnnualAudit", Objects.requireNonNull(jobRepository))
                .<AnnualMovementLine, AnnualMovement>chunk(chunkSize, Objects.requireNonNull(transactionManager))
                .reader(Objects.requireNonNull(synchronizedAnnualMovementReader))
                .processor(Objects.requireNonNull(annualMovementProcessor))
                .writer(Objects.requireNonNull(annualAuditItemWriter))
                .stepOperations(Objects.requireNonNull(batchStepOperations))
                .faultTolerant()
                .processorNonTransactional()
                .skipPolicy(Objects.requireNonNull(domainSkipPolicy))
                .retryPolicy(Objects.requireNonNull(transientDataAccessRetryPolicy))
                .backOffPolicy(Objects.requireNonNull(transientDataAccessBackOffPolicy))
                .listener(Objects.requireNonNull(loggingRetryListener))
                .listener(new LoggingSkipListener<AnnualMovementLine, AnnualMovement>())
                .listener(Objects.requireNonNull(stepMetricsListener))
                .listener(Objects.requireNonNull(chunkThroughputListener))
                .build();
    }

    @Bean
    public Job annualGenerationJob(
            JobRepository jobRepository,
            Step checkAnnualMigrationNotDone,
            Step compileAnnualAudit,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("annualGenerationJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkAnnualMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkAnnualMigrationNotDone).on("*").to(Objects.requireNonNull(compileAnnualAudit))
                .end()
                .build();
    }
}
