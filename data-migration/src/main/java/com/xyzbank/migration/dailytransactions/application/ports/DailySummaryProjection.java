package com.xyzbank.migration.dailytransactions.application.ports;

/**
 * Rebuilds the per-date summary from every published daily report. Running it
 * again replaces the summary rows instead of adding to them.
 */
public interface DailySummaryProjection {

    void rebuild();
}
