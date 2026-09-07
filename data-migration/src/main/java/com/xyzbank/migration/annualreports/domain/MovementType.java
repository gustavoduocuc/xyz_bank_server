package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.DomainError;

import java.text.Normalizer;

public enum MovementType {
    DEPOSIT,
    WITHDRAWAL,
    PURCHASE;

    public static MovementType from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw DomainError.validation("Movement type cannot be empty");
        }
        String normalized = withoutAccents(raw.trim().toLowerCase());
        return switch (normalized) {
            case "deposito" -> DEPOSIT;
            case "retiro" -> WITHDRAWAL;
            case "compra" -> PURCHASE;
            default -> throw DomainError.validation(
                    "Unknown movement type: " + raw + " (allowed: deposito, retiro, compra)"
            );
        };
    }

    public boolean isOutgoing() {
        return this == WITHDRAWAL || this == PURCHASE;
    }

    private static String withoutAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
