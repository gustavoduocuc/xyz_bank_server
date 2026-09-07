package com.xyzbank.migration.dailytransactions.infrastructure.batch;

public record DailyTransactionLine(String id, String fecha, Double monto, String tipo) {
}
