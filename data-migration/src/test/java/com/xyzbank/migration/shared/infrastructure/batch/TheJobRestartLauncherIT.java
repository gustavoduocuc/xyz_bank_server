package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "migration.batch.max-restarts=3")
@DisplayName("The job restart launcher")
class TheJobRestartLauncherIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Starts a new instance when the job never ran
     * 2. Restarts a failed execution on the same instance until it completes
     * 3. Marks an execution orphaned by a crashed process as failed and restarts it
     * 4. Gives up after max-restarts restarts
     * 5. Starts a new instance with the next run id after a completed execution
     * 6. Starts a new instance when the input file changes
     */

    private static final String fileA = "file:data/a.csv";
    private static final String fileB = "file:data/b.csv";

    @Autowired
    private JobRestartLauncher launcher;

    @Autowired
    @Qualifier("flakyJob")
    private Job flakyJob;

    @Autowired
    private FailingTasklet failingTasklet;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearMigrationData(jdbcTemplate);
        failingTasklet.failNextRuns(0);
    }

    @Test
    @DisplayName("starts a new instance when the job never ran")
    void startsANewInstanceWhenTheJobNeverRan() throws Exception {
        JobExecution execution = launcher.launch(flakyJob, fileA);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(1, instances());
    }

    @Test
    @DisplayName("restarts a failed execution on the same instance until it completes")
    void restartsAFailedExecutionOnTheSameInstanceUntilItCompletes() throws Exception {
        failingTasklet.failNextRuns(2);

        JobExecution execution = launcher.launch(flakyJob, fileA);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(1, instances());
        assertEquals(3, executionsOf(execution));
    }

    @Test
    @DisplayName("marks an execution orphaned by a crashed process as failed and restarts it")
    void marksAnExecutionOrphanedByACrashedProcessAsFailedAndRestartsIt() throws Exception {
        JobExecution orphan = jobRepository.createJobExecution("flakyJob", new JobParametersBuilder()
                .addString(JobRestartLauncher.inputFileParameter, fileA)
                .addLong("run.id", 1L)
                .toJobParameters());
        orphan.setStatus(BatchStatus.STARTED);
        jobRepository.update(orphan);

        JobExecution execution = launcher.launch(flakyJob, fileA);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(orphan.getJobInstance().getId(), execution.getJobInstance().getId());
        assertEquals(BatchStatus.FAILED, jobExplorer.getJobExecution(orphan.getId()).getStatus());
    }

    @Test
    @DisplayName("gives up after max-restarts restarts")
    void givesUpAfterMaxRestartsRestarts() {
        failingTasklet.failNextRuns(Integer.MAX_VALUE);

        assertThrows(MigrationRestartLimitExceeded.class, () -> launcher.launch(flakyJob, fileA));

        assertEquals(1, instances());
        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Integer.class));
    }

    @Test
    @DisplayName("starts a new instance with the next run id after a completed execution")
    void startsANewInstanceWithTheNextRunIdAfterACompletedExecution() throws Exception {
        JobExecution first = launcher.launch(flakyJob, fileA);

        JobExecution second = launcher.launch(flakyJob, fileA);

        assertEquals(BatchStatus.COMPLETED, second.getStatus());
        assertEquals(2, instances());
        assertEquals(first.getJobParameters().getLong("run.id") + 1, second.getJobParameters().getLong("run.id"));
    }

    @Test
    @DisplayName("starts a new instance when the input file changes")
    void startsANewInstanceWhenTheInputFileChanges() throws Exception {
        failingTasklet.failNextRuns(Integer.MAX_VALUE);
        assertThrows(MigrationRestartLimitExceeded.class, () -> launcher.launch(flakyJob, fileA));
        failingTasklet.failNextRuns(0);

        JobExecution execution = launcher.launch(flakyJob, fileB);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(fileB, execution.getJobParameters().getString(JobRestartLauncher.inputFileParameter));
        assertEquals(2, instances());
    }

    private int instances() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE", Integer.class);
        return count == null ? 0 : count;
    }

    private int executionsOf(JobExecution execution) {
        return jobExplorer.getJobExecutions(execution.getJobInstance()).size();
    }

    static class FailingTasklet implements Tasklet {

        private final AtomicInteger failuresLeft = new AtomicInteger();

        void failNextRuns(int failures) {
            failuresLeft.set(failures);
        }

        @Override
        public RepeatStatus execute(
                @NonNull StepContribution contribution,
                @NonNull ChunkContext chunkContext
        ) {
            if (failuresLeft.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0) {
                throw new IllegalStateException("simulated failure");
            }
            return RepeatStatus.FINISHED;
        }
    }

    @TestConfiguration
    static class FlakyJobConfig {

        @Bean
        FailingTasklet failingTasklet() {
            return new FailingTasklet();
        }

        @Bean
        Job flakyJob(JobRepository jobRepository, PlatformTransactionManager transactionManager, FailingTasklet failingTasklet) {
            return new JobBuilder("flakyJob", jobRepository)
                    .incrementer(new RunIdIncrementer())
                    .start(new StepBuilder("flakyStep", jobRepository)
                            .tasklet(failingTasklet, transactionManager)
                            .build())
                    .build();
        }
    }
}
