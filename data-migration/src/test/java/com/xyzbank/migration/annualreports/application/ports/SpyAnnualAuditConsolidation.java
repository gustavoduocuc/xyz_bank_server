package com.xyzbank.migration.annualreports.application.ports;

public class SpyAnnualAuditConsolidation implements AnnualAuditConsolidation {

    private int rebuilds;

    @Override
    public void rebuild() {
        rebuilds++;
    }

    public int rebuilds() {
        return rebuilds;
    }
}
