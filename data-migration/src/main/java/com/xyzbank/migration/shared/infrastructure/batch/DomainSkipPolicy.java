package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.domain.DomainError;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.lang.NonNull;

public class DomainSkipPolicy implements SkipPolicy {

    private final int skipLimit;

    public DomainSkipPolicy(int skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(@NonNull Throwable throwable, long skipCount) throws SkipLimitExceededException {
        if (!isSkippable(throwable)) {
            return false;
        }
        if (skipCount >= skipLimit) {
            throw new SkipLimitExceededException(skipLimit, throwable);
        }
        return true;
    }

    private boolean isSkippable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DomainError || current instanceof FlatFileParseException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
