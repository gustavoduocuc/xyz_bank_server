package com.xyzbank.migration.annualreports.application.ports;

import com.xyzbank.migration.annualreports.domain.AnnualAccountSummary;

import java.util.List;

public interface AnnualAuditWriter {

    void write(List<AnnualAccountSummary> summaries);
}
