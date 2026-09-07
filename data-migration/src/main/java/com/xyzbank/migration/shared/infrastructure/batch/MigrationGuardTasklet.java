package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;

public class MigrationGuardTasklet implements Tasklet {

    public static final String alreadyMigratedExitCode = "ALREADY_MIGRATED";

    private static final Logger logger = LoggerFactory.getLogger(MigrationGuardTasklet.class);

    private final MigrationExecutionPort migrationExecutionPort;
    private final String jobName;

    public MigrationGuardTasklet(MigrationExecutionPort migrationExecutionPort, String jobName) {
        this.migrationExecutionPort = migrationExecutionPort;
        this.jobName = jobName;
    }

    @Override
    public RepeatStatus execute(@NonNull StepContribution contribution, @NonNull ChunkContext chunkContext) {
        if (migrationExecutionPort.hasSuccessfulExecution(jobName)) {
            logger.info("Migration already completed for {}, skipping", jobName);
            contribution.setExitStatus(new ExitStatus(alreadyMigratedExitCode));
            return RepeatStatus.FINISHED;
        }
        logger.info("No previous successful migration found for {}, continuing", jobName);
        return RepeatStatus.FINISHED;
    }
}
