package com.xyzbank.migration.shared.infrastructure.batch;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

public class TransientDataAccessRetryPolicy extends SimpleRetryPolicy {

    public TransientDataAccessRetryPolicy(int maxAttempts) {
        super(maxAttempts, Map.of(TransientDataAccessException.class, true), true);
    }
}
