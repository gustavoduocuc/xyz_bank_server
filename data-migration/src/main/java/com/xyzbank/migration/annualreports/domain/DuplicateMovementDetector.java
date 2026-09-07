package com.xyzbank.migration.annualreports.domain;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateMovementDetector {

    private final Set<String> seenBusinessKeys = ConcurrentHashMap.newKeySet();

    public boolean isDuplicate(String businessKey) {
        return !seenBusinessKeys.add(businessKey);
    }
}
