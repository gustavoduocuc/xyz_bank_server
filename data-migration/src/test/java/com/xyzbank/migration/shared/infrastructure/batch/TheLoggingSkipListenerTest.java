package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TheLoggingSkipListenerTest {

    /*
     * Cases:
     * 1. Logs skip events without failing
     */

    @Nested
    class TheLoggingSkipListener {

        @Test
        void logsSkipEventsWithoutFailing() {
            LoggingSkipListener<String, String> listener = new LoggingSkipListener<>();

            assertDoesNotThrow(() -> listener.onSkipInRead(new RuntimeException("read failed")));
            assertDoesNotThrow(() -> listener.onSkipInProcess("item-1", new RuntimeException("process failed")));
            assertDoesNotThrow(() -> listener.onSkipInWrite("item-2", new RuntimeException("write failed")));
        }
    }
}
