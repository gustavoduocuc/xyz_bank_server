package com.xyzbank.migration.shared.application.ports;

public interface MigrationExecutionPort {

    boolean hasSuccessfulExecution(String jobName);

    void markSuccess(String jobName, int writeCount, int skipCount);

    void markFailed(String jobName);
}
