package com.xyzbank.migration.dailytransactions.domain;

import com.xyzbank.migration.shared.domain.DomainError;

import java.text.Normalizer;

public enum TransactionType {
    DEBIT,
    CREDIT;

    public static TransactionType from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw DomainError.validation("Transaction type cannot be empty");
        }
        String normalized = withoutAccents(raw.trim().toLowerCase());
        return switch (normalized) {
            case "debito" -> DEBIT;
            case "credito" -> CREDIT;
            default -> throw DomainError.validation(
                    "Unknown transaction type: " + raw + " (allowed: debito, credito)"
            );
        };
    }

    private static String withoutAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
