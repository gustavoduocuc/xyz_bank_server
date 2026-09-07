package com.xyzbank.migration.dailytransactions.application.ports;

import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;

import java.util.List;

public interface DailyReportWriter {

    void write(List<ProcessedTransaction> transactions);
}
