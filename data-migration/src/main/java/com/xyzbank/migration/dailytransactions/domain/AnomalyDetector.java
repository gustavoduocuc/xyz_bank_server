package com.xyzbank.migration.dailytransactions.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AnomalyDetector {

    private final Set<String> seenBusinessKeys = ConcurrentHashMap.newKeySet();

    public ProcessedTransaction evaluate(Transaction transaction) {
        double highAmountThreshold = 2000;
        List<AnomalyType> anomalies = new ArrayList<>();

        if (transaction.exceedsAmount(highAmountThreshold)) {
            anomalies.add(AnomalyType.HIGH_AMOUNT);
        }

        if (isDuplicateBusinessKey(transaction.businessKey())) {
            anomalies.add(AnomalyType.DUPLICATE);
        }

        if (anomalies.isEmpty()) {
            return ProcessedTransaction.withoutAnomalies(transaction);
        }
        return ProcessedTransaction.withAnomalies(transaction, anomalies);
    }

    private boolean isDuplicateBusinessKey(String businessKey) {
        boolean alreadySeen = !seenBusinessKeys.add(businessKey);
        return alreadySeen;
    }
}
