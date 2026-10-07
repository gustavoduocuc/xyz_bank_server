package com.xyzbank.migration.shared.infrastructure.batch;

public class MigrationRestartLimitExceeded extends RuntimeException {

    public MigrationRestartLimitExceeded(String jobName, int maxRestarts) {
        super(jobName + " still failing after " + maxRestarts + " restarts");
    }
}
