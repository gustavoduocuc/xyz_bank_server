package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the three migrations in order as a one-shot process. A failed job is restarted
 * from its last committed chunk by {@link JobRestartLauncher}; if it still fails, the
 * remaining jobs are not launched and the process exits with code 1 so the container
 * can be restarted.
 */
@Component
public class RunAllMigrationsRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RunAllMigrationsRunner.class);

    private static final int success = 0;
    private static final int failure = 1;

    private record MigrationJob(Job job, String inputFile) {
    }

    private final boolean runAll;
    private final JobRestartLauncher jobRestartLauncher;
    private final List<MigrationJob> jobs;
    private final ConfigurableApplicationContext applicationContext;

    public RunAllMigrationsRunner(
            @Value("${migration.run-all:false}") boolean runAll,
            JobRestartLauncher jobRestartLauncher,
            @Qualifier("dailyTransactionsJob") Job dailyTransactionsJob,
            @Qualifier("monthlyInterestsJob") Job monthlyInterestsJob,
            @Qualifier("annualGenerationJob") Job annualGenerationJob,
            @Value("${migration.data.daily-transactions}") String dailyTransactionsFile,
            @Value("${migration.data.monthly-interests}") String monthlyInterestsFile,
            @Value("${migration.data.annual-accounts}") String annualAccountsFile,
            ConfigurableApplicationContext applicationContext) {
        this.runAll = runAll;
        this.jobRestartLauncher = jobRestartLauncher;
        this.jobs = List.of(
                new MigrationJob(dailyTransactionsJob, dailyTransactionsFile),
                new MigrationJob(monthlyInterestsJob, monthlyInterestsFile),
                new MigrationJob(annualGenerationJob, annualAccountsFile));
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!runAll) {
            return;
        }
        int exitCode = runJobs();
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    int runJobs() {
        for (MigrationJob migration : jobs) {
            if (!completes(migration)) {
                return failure;
            }
        }
        return success;
    }

    private boolean completes(MigrationJob migration) {
        String jobName = migration.job().getName();
        logger.info("Launching {} input={}", jobName, migration.inputFile());
        try {
            JobExecution execution = jobRestartLauncher.launch(migration.job(), migration.inputFile());
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                return true;
            }
            logger.error("{} finished with status {}; remaining migrations not launched", jobName, execution.getStatus());
        } catch (MigrationRestartLimitExceeded exception) {
            logger.error("{}; remaining migrations not launched", exception.getMessage());
        } catch (Exception exception) {
            logger.error("{} could not be launched; remaining migrations not launched", jobName, exception);
        }
        return false;
    }
}
