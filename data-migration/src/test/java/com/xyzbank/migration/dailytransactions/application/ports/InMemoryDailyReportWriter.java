package com.xyzbank.migration.dailytransactions.application.ports;

import com.xyzbank.migration.dailytransactions.domain.ProcessedTransaction;

import java.util.ArrayList;
import java.util.List;

public class InMemoryDailyReportWriter implements DailyReportWriter {

    private final List<ProcessedTransaction> written = new ArrayList<>();

    @Override
    public void write(List<ProcessedTransaction> transactions) {
        written.addAll(transactions);
    }

    public List<ProcessedTransaction> written() {
        return List.copyOf(written);
    }
}
