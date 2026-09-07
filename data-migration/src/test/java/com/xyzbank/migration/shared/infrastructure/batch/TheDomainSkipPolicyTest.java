package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.domain.DomainError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.item.file.FlatFileParseException;

import static org.junit.jupiter.api.Assertions.*;

class TheDomainSkipPolicyTest {

    /*
     * Cases:
     * 1. Skips DomainError under limit
     * 2. Skips FlatFileParseException under limit
     * 3. Does not skip unrelated exceptions
     * 4. Exceeds skip limit for DomainError
     */

    @Nested
    class TheDomainSkipPolicy {

        @Test
        void skipsDomainErrorUnderLimit() throws Exception {
            DomainSkipPolicy policy = new DomainSkipPolicy(100);

            assertTrue(policy.shouldSkip(DomainError.validation("bad amount"), 0));
        }

        @Test
        void skipsFlatFileParseExceptionUnderLimit() throws Exception {
            DomainSkipPolicy policy = new DomainSkipPolicy(100);

            assertTrue(policy.shouldSkip(new FlatFileParseException("parse", "line"), 0));
        }

        @Test
        void doesNotSkipUnrelatedExceptions() throws Exception {
            DomainSkipPolicy policy = new DomainSkipPolicy(100);

            assertFalse(policy.shouldSkip(new IllegalStateException("boom"), 0));
        }

        @Test
        void exceedsSkipLimitForDomainError() {
            DomainSkipPolicy policy = new DomainSkipPolicy(2);

            assertThrows(
                    SkipLimitExceededException.class,
                    () -> policy.shouldSkip(DomainError.validation("bad"), 2)
            );
        }
    }
}
