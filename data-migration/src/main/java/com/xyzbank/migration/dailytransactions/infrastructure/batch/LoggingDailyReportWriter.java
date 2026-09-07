package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.DailyReportWriter;
import com.xyzbank.migration.dailytransactions.domain.AnomalyType;
import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LoggingDailyReportWriter implements DailyReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDailyReportWriter.class);

    @Override
    public void write(List<ProcessedTransaction> transactions) {
        Map<AnomalyType, Integer> anomalyCounts = new EnumMap<>(AnomalyType.class);

        for (ProcessedTransaction processed : transactions) {
            logProcessedTransaction(processed);
            countAnomalies(processed, anomalyCounts);
        }

        logger.info(
                "Daily chunk summary written={} anomalyCounts={}",
                transactions.size(),
                anomalyCounts
        );
    }

    private void logProcessedTransaction(ProcessedTransaction processed) {
        logger.info(
                "Daily transaction processed id={} date={} amount={} type={} anomalies={}",
                processed.idValue(),
                processed.dateAsIso(),
                processed.amountValue(),
                processed.type(),
                processed.anomalies()
        );
    }

    private void countAnomalies(ProcessedTransaction processed, Map<AnomalyType, Integer> anomalyCounts) {
        for (AnomalyType anomaly : processed.anomalies()) {
            anomalyCounts.merge(anomaly, 1, Integer::sum);
        }
    }
}
