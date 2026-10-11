package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
