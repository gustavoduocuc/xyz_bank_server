package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.domain.AnomalyDetector;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import com.xyzbank.migration.dailytransactions.domain.Transaction;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.infrastructure.batch.CsvFieldNormalizer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

public class DailyTransactionProcessor implements ItemProcessor<DailyTransactionLine, ProcessedTransaction> {

    private final AnomalyDetector anomalyDetector;

    public DailyTransactionProcessor(AnomalyDetector anomalyDetector) {
        this.anomalyDetector = anomalyDetector;
    }

    @Override
    public ProcessedTransaction process(@NonNull DailyTransactionLine line) {
        Double amount = CsvFieldNormalizer.scaleAmount(line.monto());
        if (amount == null) {
            throw DomainError.validation("Transaction amount cannot be empty");
        }

        Transaction transaction = Transaction.create(
                CsvFieldNormalizer.text(line.id()),
                CsvFieldNormalizer.text(line.fecha()),
                amount,
                CsvFieldNormalizer.text(line.tipo())
        );
        ProcessedTransaction processed = anomalyDetector.evaluate(transaction);

        if (processed.isDuplicate()) {
            throw DomainError.validation("Duplicate transaction skipped: " + transaction.businessKey());
        }

        return processed;
    }
}
