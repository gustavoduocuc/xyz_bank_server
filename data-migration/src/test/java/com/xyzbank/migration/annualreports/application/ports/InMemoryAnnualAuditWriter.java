package com.xyzbank.migration.annualreports.application.ports;

import com.xyzbank.migration.annualreports.domain.AnnualAccountSummary;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAnnualAuditWriter implements AnnualAuditWriter {

    private final List<AnnualAccountSummary> written = new ArrayList<>();

    @Override
    public void write(List<AnnualAccountSummary> summaries) {
        written.addAll(summaries);
    }

    public List<AnnualAccountSummary> written() {
        return List.copyOf(written);
    }
}
