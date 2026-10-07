package com.xyzbank.migration.dailytransactions.application.ports;

public class SpyDailyReportPublication implements DailyReportPublication {

    private int publications;

    @Override
    public void publish() {
        publications++;
    }

    public int publications() {
        return publications;
    }
}
