package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.context.RetryContextSupport;

import static org.junit.jupiter.api.Assertions.*;

class TheTransientDataAccessRetryPolicyTest {

    /*
     * Cases:
     * 1. Retries transient data access exceptions
     * 2. Does not retry unrelated exceptions
     */

    @Nested
    class TheTransientDataAccessRetryPolicy {

        @Test
        void retriesTransientDataAccessExceptions() {
            TransientDataAccessRetryPolicy policy = new TransientDataAccessRetryPolicy(3);
            RetryContextSupport context = (RetryContextSupport) policy.open(null);
            context.registerThrowable(new CannotAcquireLockException("lock contention"));

            assertTrue(policy.canRetry(context));
        }

        @Test
        void doesNotRetryUnrelatedExceptions() {
            TransientDataAccessRetryPolicy policy = new TransientDataAccessRetryPolicy(3);
            RetryContextSupport context = (RetryContextSupport) policy.open(null);
            context.registerThrowable(new IllegalStateException("boom"));

            assertFalse(policy.canRetry(context));
        }
    }
}
