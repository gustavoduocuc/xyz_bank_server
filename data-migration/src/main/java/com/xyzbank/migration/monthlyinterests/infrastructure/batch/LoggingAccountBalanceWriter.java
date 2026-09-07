package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LoggingAccountBalanceWriter implements AccountBalanceWriter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAccountBalanceWriter.class);

    @Override
    public void write(List<InterestApplied> balances) {
        for (InterestApplied applied : balances) {
            logger.info(
                    "Monthly interest applied accountId={} name={} type={} rate={} previousBalance={} finalBalance={}",
                    applied.accountIdValue(),
                    applied.accountName(),
                    applied.accountType(),
                    applied.rate(),
                    applied.previousBalanceValue(),
                    applied.finalBalanceValue()
            );
        }
    }
}
