package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning of the migration jobs, bound from {@code migration.batch.*}.
 *
 * @param chunkSize     rows committed per chunk
 * @param throttleLimit ranges processed in parallel per job
 * @param maxRestarts   restarts allowed for a failed job before the process gives up
 * @param skipLimit     domain and parse errors tolerated per worker step
 * @param retryLimit    attempts for a write that fails with a transient error
 */
@ConfigurationProperties(prefix = "migration.batch")
public record MigrationBatchSettings(
        int chunkSize,
        int throttleLimit,
        int maxRestarts,
        int skipLimit,
        int retryLimit
) {

    public int stepStartLimit() {
        return maxRestarts + 1;
    }
}
