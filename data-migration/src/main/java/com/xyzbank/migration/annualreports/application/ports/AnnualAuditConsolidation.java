package com.xyzbank.migration.annualreports.application.ports;

/**
 * Rebuilds the per-account annual audit from every stored movement. Running it
 * again replaces the totals instead of adding to them.
 */
public interface AnnualAuditConsolidation {

    void rebuild();
}
