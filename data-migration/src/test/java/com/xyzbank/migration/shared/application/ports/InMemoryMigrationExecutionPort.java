package com.xyzbank.migration.shared.application.ports;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMigrationExecutionPort implements MigrationExecutionPort {

    private final Map<String, ExecutionRecord> executions = new HashMap<>();

    @Override
    public boolean hasSuccessfulExecution(String jobName) {
        ExecutionRecord record = executions.get(jobName);
        return record != null && "SUCCESS".equals(record.status());
    }

    @Override
    public void markSuccess(String jobName, int writeCount, int skipCount) {
        executions.put(jobName, new ExecutionRecord("SUCCESS", Instant.now(), writeCount, skipCount));
    }

    @Override
    public void markFailed(String jobName) {
        executions.put(jobName, new ExecutionRecord("FAILED", Instant.now(), null, null));
    }

    public void clear() {
        executions.clear();
    }

    public ExecutionRecord executionFor(String jobName) {
        return executions.get(jobName);
    }

    public record ExecutionRecord(String status, Instant executedAt, Integer writeCount, Integer skipCount) {
    }
}
