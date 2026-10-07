package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.DuplicateAccountDetector;
import com.xyzbank.migration.monthlyinterests.infrastructure.adapters.JdbcAccountBalanceWriter;
import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import com.xyzbank.migration.shared.infrastructure.batch.CsvLineRangePartitioner;
import com.xyzbank.migration.shared.infrastructure.batch.JobSummaryListener;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationGuardTasklet;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationLedgerListener;
import com.xyzbank.migration.shared.infrastructure.batch.MigrationStepFactory;
import com.xyzbank.migration.shared.infrastructure.batch.NumberedLineMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

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
            @Value("${migration.data.monthly-interests}") Resource resource,
            @Value("#{stepExecutionContext['startItem']}") int startItem,
            @Value("#{stepExecutionContext['endItem']}") int endItem
    ) {
        return new FlatFileItemReaderBuilder<InterestAccountLine>()
                .name("monthlyInterestReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .currentItemCount(startItem)
                .maxItemCount(endItem)
                .lineMapper(NumberedLineMapper.delimited(
                        new InterestAccountLineMapper(), "cuentaId", "nombre", "saldo", "edad", "tipo"))
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
            MigrationStepFactory migrationStepFactory,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return migrationStepFactory.tasklet(
                "checkMonthlyMigrationNotDone",
                new MigrationGuardTasklet(migrationExecutionPort, "monthlyInterestsJob"));
    }

    @Bean
    public Step calculateMonthlyInterestsWorker(
            MigrationStepFactory migrationStepFactory,
            FlatFileItemReader<InterestAccountLine> monthlyInterestReader,
            MonthlyInterestProcessor monthlyInterestProcessor,
            AccountBalanceItemWriter accountBalanceItemWriter
    ) {
        return migrationStepFactory.worker(
                "calculateMonthlyInterestsWorker",
                monthlyInterestReader,
                monthlyInterestProcessor,
                accountBalanceItemWriter);
    }

    @Bean
    public Step calculateMonthlyInterestsManager(
            MigrationStepFactory migrationStepFactory,
            Step calculateMonthlyInterestsWorker,
            @Value("${migration.data.monthly-interests}") Resource resource
    ) {
        return migrationStepFactory.manager(
                "calculateMonthlyInterestsManager",
                calculateMonthlyInterestsWorker,
                new CsvLineRangePartitioner(resource, 1));
    }

    @Bean
    public Job monthlyInterestsJob(
            JobRepository jobRepository,
            Step checkMonthlyMigrationNotDone,
            Step calculateMonthlyInterestsManager,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("monthlyInterestsJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkMonthlyMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkMonthlyMigrationNotDone).on("*").to(Objects.requireNonNull(calculateMonthlyInterestsManager))
                .end()
                .build();
    }
}
