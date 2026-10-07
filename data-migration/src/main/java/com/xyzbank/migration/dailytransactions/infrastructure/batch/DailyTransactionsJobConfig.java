package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportPublication;
import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.application.ports.DailySummaryProjection;
import com.xyzbank.migration.dailytransactions.domain.AnomalyDetector;
import com.xyzbank.migration.dailytransactions.infrastructure.adapters.JdbcDailyReportPublication;
import com.xyzbank.migration.dailytransactions.infrastructure.adapters.JdbcDailyReportWriter;
import com.xyzbank.migration.dailytransactions.infrastructure.adapters.JdbcDailySummaryProjection;
import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import com.xyzbank.migration.shared.infrastructure.batch.ActionTasklet;
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
    public DailyReportPublication dailyReportPublication(JdbcTemplate jdbcTemplate) {
        return new JdbcDailyReportPublication(jdbcTemplate);
    }

    @Bean
    public DailySummaryProjection dailySummaryProjection(JdbcTemplate jdbcTemplate) {
        return new JdbcDailySummaryProjection(jdbcTemplate);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<DailyTransactionLine> dailyTransactionReader(
            @Value("${migration.data.daily-transactions}") Resource resource,
            @Value("#{stepExecutionContext['startItem']}") int startItem,
            @Value("#{stepExecutionContext['endItem']}") int endItem
    ) {
        return new FlatFileItemReaderBuilder<DailyTransactionLine>()
                .name("dailyTransactionReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .currentItemCount(startItem)
                .maxItemCount(endItem)
                .lineMapper(NumberedLineMapper.delimited(new DailyTransactionLineMapper(), "id", "fecha", "monto", "tipo"))
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
            MigrationStepFactory migrationStepFactory,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return migrationStepFactory.tasklet(
                "checkDailyMigrationNotDone",
                new MigrationGuardTasklet(migrationExecutionPort, "dailyTransactionsJob"));
    }

    @Bean
    public Step processDailyTransactionsWorker(
            MigrationStepFactory migrationStepFactory,
            FlatFileItemReader<DailyTransactionLine> dailyTransactionReader,
            DailyTransactionProcessor dailyTransactionProcessor,
            DailyReportItemWriter dailyReportItemWriter
    ) {
        return migrationStepFactory.worker(
                "processDailyTransactionsWorker",
                dailyTransactionReader,
                dailyTransactionProcessor,
                dailyReportItemWriter);
    }

    @Bean
    public Step processDailyTransactionsManager(
            MigrationStepFactory migrationStepFactory,
            Step processDailyTransactionsWorker,
            @Value("${migration.data.daily-transactions}") Resource resource
    ) {
        return migrationStepFactory.manager(
                "processDailyTransactionsManager",
                processDailyTransactionsWorker,
                new CsvLineRangePartitioner(resource, 1));
    }

    @Bean
    public Step publishDailyTransactions(
            MigrationStepFactory migrationStepFactory,
            DailyReportPublication dailyReportPublication
    ) {
        return migrationStepFactory.tasklet(
                "publishDailyTransactions",
                new ActionTasklet(dailyReportPublication::publish));
    }

    @Bean
    public Step summarizeDailyTransactions(
            MigrationStepFactory migrationStepFactory,
            DailySummaryProjection dailySummaryProjection
    ) {
        return migrationStepFactory.tasklet(
                "summarizeDailyTransactions",
                new ActionTasklet(dailySummaryProjection::rebuild));
    }

    @Bean
    public Job dailyTransactionsJob(
            JobRepository jobRepository,
            Step checkDailyMigrationNotDone,
            Step processDailyTransactionsManager,
            Step publishDailyTransactions,
            Step summarizeDailyTransactions,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("dailyTransactionsJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkDailyMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkDailyMigrationNotDone).on("*").to(Objects.requireNonNull(processDailyTransactionsManager))
                .next(Objects.requireNonNull(publishDailyTransactions))
                .next(Objects.requireNonNull(summarizeDailyTransactions))
                .end()
                .build();
    }
}
