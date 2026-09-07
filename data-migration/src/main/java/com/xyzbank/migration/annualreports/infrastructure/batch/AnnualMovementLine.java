package com.xyzbank.migration.annualreports.infrastructure.batch;

public record AnnualMovementLine(String cuentaId, String fecha, String transaccion, Double monto, String descripcion) {
}
