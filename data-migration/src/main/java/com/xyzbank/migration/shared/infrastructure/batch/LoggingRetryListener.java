package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class LoggingRetryListener implements RetryListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingRetryListener.class);

    @Override
    public <T, E extends Throwable> void onError(
            RetryContext context,
            RetryCallback<T, E> callback,
            Throwable throwable
    ) {
        logger.info(
                "Transient JDBC error attempt={} thread={} exceptionType={} reason={}",
                context.getRetryCount(),
                Thread.currentThread().getName(),
                throwable.getClass().getSimpleName(),
                throwable.getMessage()
        );
    }
}
