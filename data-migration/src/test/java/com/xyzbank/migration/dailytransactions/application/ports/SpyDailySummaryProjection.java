package com.xyzbank.migration.dailytransactions.application.ports;

public class SpyDailySummaryProjection implements DailySummaryProjection {

    private int rebuilds;

    @Override
    public void rebuild() {
        rebuilds++;
    }

    public int rebuilds() {
        return rebuilds;
    }
}
