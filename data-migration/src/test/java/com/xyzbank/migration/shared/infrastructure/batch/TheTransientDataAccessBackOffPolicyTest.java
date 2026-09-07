package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

import static org.junit.jupiter.api.Assertions.*;

class TheTransientDataAccessBackOffPolicyTest {

    /*
     * Cases:
     * 1. Uses exponential backoff with expected intervals
     */

    @Nested
    class TheTransientDataAccessBackOffPolicy {

        @Test
        void usesExponentialBackoffWithExpectedIntervals() {
            SharedBatchConfig config = new SharedBatchConfig();

            BackOffPolicy backOffPolicy = config.transientDataAccessBackOffPolicy();

            assertInstanceOf(ExponentialBackOffPolicy.class, backOffPolicy);
            ExponentialBackOffPolicy exponential = (ExponentialBackOffPolicy) backOffPolicy;
            assertEquals(1000L, exponential.getInitialInterval());
            assertEquals(2.0, exponential.getMultiplier());
            assertEquals(10000L, exponential.getMaxInterval());
        }
    }
}
