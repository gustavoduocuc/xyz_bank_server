package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditConsolidation;
import com.xyzbank.migration.annualreports.application.ports.AnnualMovementStore;
import com.xyzbank.migration.annualreports.domain.DuplicateMovementDetector;
import com.xyzbank.migration.annualreports.infrastructure.adapters.JdbcAnnualAuditConsolidation;
import com.xyzbank.migration.annualreports.infrastructure.adapters.JdbcAnnualMovementStore;
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
public class AnnualGenerationJobConfig {

    @Bean
    public AnnualMovementStore annualMovementStore(JdbcTemplate jdbcTemplate) {
        return new JdbcAnnualMovementStore(jdbcTemplate);
    }

    @Bean
    public AnnualAuditConsolidation annualAuditConsolidation(JdbcTemplate jdbcTemplate) {
        return new JdbcAnnualAuditConsolidation(jdbcTemplate);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<AnnualMovementLine> annualMovementReader(
            @Value("${migration.data.annual-accounts}") Resource resource,
            @Value("#{stepExecutionContext['startItem']}") int startItem,
            @Value("#{stepExecutionContext['endItem']}") int endItem
    ) {
        return new FlatFileItemReaderBuilder<AnnualMovementLine>()
                .name("annualMovementReader")
                .resource(Objects.requireNonNull(resource))
                .linesToSkip(1)
                .currentItemCount(startItem)
                .maxItemCount(endItem)
                .lineMapper(NumberedLineMapper.delimited(
                        new AnnualMovementLineMapper(), "cuentaId", "fecha", "transaccion", "monto", "descripcion"))
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
    public AnnualMovementItemWriter annualMovementItemWriter(AnnualMovementStore annualMovementStore) {
        return new AnnualMovementItemWriter(annualMovementStore);
    }

    @Bean
    public Step checkAnnualMigrationNotDone(
            MigrationStepFactory migrationStepFactory,
            MigrationExecutionPort migrationExecutionPort
    ) {
        return migrationStepFactory.tasklet(
                "checkAnnualMigrationNotDone",
                new MigrationGuardTasklet(migrationExecutionPort, "annualGenerationJob"));
    }

    @Bean
    public Step stageAnnualMovementsWorker(
            MigrationStepFactory migrationStepFactory,
            FlatFileItemReader<AnnualMovementLine> annualMovementReader,
            AnnualMovementProcessor annualMovementProcessor,
            AnnualMovementItemWriter annualMovementItemWriter
    ) {
        return migrationStepFactory.worker(
                "stageAnnualMovementsWorker",
                annualMovementReader,
                annualMovementProcessor,
                annualMovementItemWriter);
    }

    @Bean
    public Step stageAnnualMovementsManager(
            MigrationStepFactory migrationStepFactory,
            Step stageAnnualMovementsWorker,
            @Value("${migration.data.annual-accounts}") Resource resource
    ) {
        return migrationStepFactory.manager(
                "stageAnnualMovementsManager",
                stageAnnualMovementsWorker,
                new CsvLineRangePartitioner(resource, 1));
    }

    @Bean
    public Step consolidateAnnualAudit(
            MigrationStepFactory migrationStepFactory,
            AnnualAuditConsolidation annualAuditConsolidation
    ) {
        return migrationStepFactory.tasklet(
                "consolidateAnnualAudit",
                new ActionTasklet(annualAuditConsolidation::rebuild));
    }

    @Bean
    public Job annualGenerationJob(
            JobRepository jobRepository,
            Step checkAnnualMigrationNotDone,
            Step stageAnnualMovementsManager,
            Step consolidateAnnualAudit,
            JobSummaryListener jobSummaryListener,
            MigrationLedgerListener migrationLedgerListener
    ) {
        return new JobBuilder("annualGenerationJob", Objects.requireNonNull(jobRepository))
                .incrementer(new RunIdIncrementer())
                .listener(Objects.requireNonNull(jobSummaryListener))
                .listener(Objects.requireNonNull(migrationLedgerListener))
                .start(Objects.requireNonNull(checkAnnualMigrationNotDone))
                .on(MigrationGuardTasklet.alreadyMigratedExitCode).end()
                .from(checkAnnualMigrationNotDone).on("*").to(Objects.requireNonNull(stageAnnualMovementsManager))
                .next(Objects.requireNonNull(consolidateAnnualAudit))
                .end()
                .build();
    }
}
