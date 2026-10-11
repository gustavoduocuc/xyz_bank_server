package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.batch.core.JobExecution;

import java.util.List;

/**
 * The earlier executions of the same job instance as {@code current}, so that a
 * restarted job can account for the work committed before its failure.
 */
@FunctionalInterface
public interface JobExecutionHistory {

    List<JobExecution> previousExecutions(JobExecution current);
}
