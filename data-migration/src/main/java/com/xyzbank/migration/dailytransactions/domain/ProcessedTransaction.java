package com.xyzbank.migration.dailytransactions.domain;

import java.util.List;

public final class ProcessedTransaction {

    private final Transaction transaction;
    private final List<AnomalyType> anomalies;

    private ProcessedTransaction(Transaction transaction, List<AnomalyType> anomalies) {
        this.transaction = transaction;
        this.anomalies = List.copyOf(anomalies);
    }

    public static ProcessedTransaction withoutAnomalies(Transaction transaction) {
        return new ProcessedTransaction(transaction, List.of());
    }

    public static ProcessedTransaction withAnomalies(Transaction transaction, List<AnomalyType> anomalies) {
        return new ProcessedTransaction(transaction, anomalies);
    }

    public Transaction transaction() {
        return transaction;
    }

    public List<AnomalyType> anomalies() {
        return anomalies;
    }

    public boolean hasAnomaly() {
        return anomalies.size() > 0;
    }

    public boolean isDuplicate() {
        return anomalies.contains(AnomalyType.DUPLICATE);
    }

    public String idValue() {
        return transaction.id().value();
    }

    public String dateAsIso() {
        return transaction.date().asIso();
    }

    public double amountValue() {
        return transaction.amount().amount();
    }

    public TransactionType type() {
        return transaction.type();
    }
}
