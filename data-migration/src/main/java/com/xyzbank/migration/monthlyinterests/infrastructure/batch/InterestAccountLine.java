package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

public record InterestAccountLine(String cuentaId, String nombre, Double saldo, Integer edad, String tipo) {
}
