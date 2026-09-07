package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.DuplicateAccountDetector;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.monthlyinterests.infrastructure.adapters.JdbcAccountBalanceWriter;
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
public class MonthlyInterestsJobConfig {

    @Bean
    public AccountBalanceWriter accountBalanceWriter(JdbcTemplate jdbcTemplate) {
        return new JdbcAccountBalanceWriter(jdbcTemplate);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InterestAccountLine> monthlyInterestReader(
            @Value("${migration.data.monthly-interests}") Resource resource
    ) {
        return new FlatFileItemReaderBuilder<InterestAccountLine>()
                .name("monthlyInterestReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(new InterestAccountLineMapper())
                .build();
    }

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<InterestAccountLine> synchronizedMonthlyInterestReader(
            @NonNull FlatFileItemReader<InterestAccountLine> monthlyInterestReader
    ) {
        return new SynchronizedItemStreamReaderBuilder<InterestAccountLine>()
                .delegate(Objects.requireNonNull(monthlyInterestReader))
                .build();
    }

    @Bean
    @StepScope
    public DuplicateAccountDetector duplicateAccountDetector() {
        return new DuplicateAccountDetector();
    }

    @Bean
    @StepScope
    public MonthlyInterestProcessor monthlyInterestProcessor(DuplicateAccountDetector duplicateAccountDetector) {
        return new MonthlyInterestProcessor(duplicateAccountDetector);
    }

    @Bean
    public AccountBalanceItemWriter accountBalanceItemWriter(AccountBalanceWriter accountBalanceWriter) {
        return new AccountBalanceItemWriter(accountBalanceWriter);
    }

    @Bean
    public Step checkMonthlyMigrationNotDone(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return new StepBuilder("checkMonthlyMigrationNotDone", Objects.requireNonNull(jobRepository))
                .tasklet(
                        new MigrationGuardTasklet(migrationExecutionPort, "monthlyInterestsJob"),
                        Objects.requireNonNull(transactionManager)
                )
                .build();
    }

    @Bean
    public Step calculateMonthlyInterests(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SynchronizedItemStreamReader<InterestAccountLine> synchronizedMonthlyInterestReader,
            MonthlyInterestProcessor monthlyInterestProcessor,
            AccountBalanceItemWriter accountBalanceItemWriter,
            RepeatOperations batchStepOperations,
            @Value("${migration.batch.chunk-size}") int chunkSize,
            DomainSkipPolicy domainSkipPolicy,
            TransientDataAccessRetryPolicy transientDataAccessRetryPolicy,
            BackOffPolicy transientDataAccessBackOffPolicy,
            LoggingRetryListener loggingRetryListener,
            StepMetricsListener stepMetricsListener,
            ChunkThroughputListener chunkThroughputListener
    ) {
        return new StepBuilder("calculateMonthlyInterests", Objects.requireNonNull(jobRepository))
                .<InterestAccountLine, InterestApplied>chunk(chunkSize, Objects.requireNonNull(transactionManager))
                .reader(Objects.requireNonNull(synchronizedMonthlyInterestReader))
                .processor(Objects.requireNonNull(monthlyInterestProcessor))
                .writer(Objects.requireNonNull(accountBalanceItemWriter))
                .stepOperations(Objects.requireNonNull(batchStepOperations))
                .faultTolerant()
                .processorNonTransactional()
                .skipPolicy(Objects.requireNonNull(domainSkipPolicy))
                .retryPolicy(Objects.requireNonNull(transientDataAccessRetryPolicy))
                .backOffPolicy(Objects.requireNonNull(transientDataAccessBackOffPolicy))
                .listener(Objects.requireNonNull(loggingRetryListener))
                .listener(new LoggingSkipListener<InterestAccountLine, InterestApplied>())
                .listener(Objects.requireNonNull(stepMetricsListener))
                .listener(Objects.requireNonNull(chunkThroughputListener))
                .build();
    }

    @Bean
    public Job monthlyInterestsJob(
            JobRepository jobRepository,
            Step checkMonthlyMigrationNotDone,
            Step calculateMonthlyInterests,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("monthlyInterestsJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkMonthlyMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkMonthlyMigrationNotDone).on("*").to(Objects.requireNonNull(calculateMonthlyInterests))
                .end()
                .build();
    }
}
