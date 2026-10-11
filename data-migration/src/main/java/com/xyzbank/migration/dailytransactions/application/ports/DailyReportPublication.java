package com.xyzbank.migration.dailytransactions.application.ports;

/**
 * Publishes the staged daily transactions as the daily reports. Running it again
 * leaves the reports unchanged.
 */
public interface DailyReportPublication {

    void publish();
}
