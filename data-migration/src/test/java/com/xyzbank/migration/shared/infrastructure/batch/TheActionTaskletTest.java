package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TheActionTaskletTest {

    /*
     * Cases:
     * 1. Runs its action once and finishes
     * 2. Propagates a failure of its action so the step fails
     */

    @Nested
    class TheActionTasklet {

        @Test
        void runsItsActionOnceAndFinishes() {
            AtomicInteger runs = new AtomicInteger();

            RepeatStatus status = new ActionTasklet(runs::incrementAndGet).execute(null, null);

            assertEquals(RepeatStatus.FINISHED, status);
            assertEquals(1, runs.get());
        }

        @Test
        void propagatesAFailureOfItsActionSoTheStepFails() {
            ActionTasklet tasklet = new ActionTasklet(() -> {
                throw new IllegalStateException("database unavailable");
            });

            assertThrows(IllegalStateException.class, () -> tasklet.execute(null, null));
        }
    }
}
