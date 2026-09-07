package com.xyzbank.migration.monthlyinterests.domain;

import com.xyzbank.migration.shared.domain.DomainError;

import java.text.Normalizer;

public enum AccountType {
    SAVINGS,
    LOAN,
    MORTGAGE;

    public static AccountType from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw DomainError.validation("Account type cannot be empty");
        }
        String normalized = withoutAccents(raw.trim().toLowerCase());
        return switch (normalized) {
            case "ahorro" -> SAVINGS;
            case "prestamo" -> LOAN;
            case "hipoteca" -> MORTGAGE;
            default -> throw DomainError.validation(
                    "Unknown account type: " + raw + " (allowed: ahorro, prestamo, hipoteca)"
            );
        };
    }

    private static String withoutAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
