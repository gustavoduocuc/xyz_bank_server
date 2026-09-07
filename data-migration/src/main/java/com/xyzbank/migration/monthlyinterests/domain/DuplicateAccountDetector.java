package com.xyzbank.migration.monthlyinterests.domain;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateAccountDetector {

    private final Set<String> seenAccountIds = ConcurrentHashMap.newKeySet();

    public boolean isDuplicate(String accountId) {
        return !seenAccountIds.add(accountId);
    }
}
