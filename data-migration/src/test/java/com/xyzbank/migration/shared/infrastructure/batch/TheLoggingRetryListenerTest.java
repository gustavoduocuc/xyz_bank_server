package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.context.RetryContextSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TheLoggingRetryListenerTest {

    /*
     * Cases:
     * 1. Logs retry events without failing
     */

    @Nested
    class TheLoggingRetryListener {

        @Test
        void logsRetryEventsWithoutFailing() {
            LoggingRetryListener listener = new LoggingRetryListener();
            RetryContextSupport context = new RetryContextSupport(null);

            assertDoesNotThrow(() -> listener.onError(
                    context,
                    (RetryCallback<Object, RuntimeException>) retryContext -> null,
                    new RuntimeException("transient jdbc failure")
            ));
        }
    }
}
