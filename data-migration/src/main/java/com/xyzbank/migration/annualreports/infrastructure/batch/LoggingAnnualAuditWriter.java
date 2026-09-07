package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditWriter;
import com.xyzbank.migration.annualreports.domain.AnnualAccountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LoggingAnnualAuditWriter implements AnnualAuditWriter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAnnualAuditWriter.class);

    @Override
    public void write(List<AnnualAccountSummary> summaries) {
        for (AnnualAccountSummary summary : summaries) {
            logger.info(
                    "Annual audit accountId={} deposits={} withdrawals={} net={} movements={}",
                    summary.accountIdValue(),
                    summary.totalDepositsValue(),
                    summary.totalWithdrawalsValue(),
                    summary.netBalanceValue(),
                    summary.movementCount()
            );
        }
    }
}
