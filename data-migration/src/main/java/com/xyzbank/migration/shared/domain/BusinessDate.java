package com.xyzbank.migration.shared.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public final class BusinessDate {

    private static final DateTimeFormatter ISO_DASH = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DAY_FIRST_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DAY_FIRST_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LocalDate value;

    private BusinessDate(LocalDate value) {
        this.value = value;
    }

    public static BusinessDate create(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw DomainError.validation("Date cannot be empty");
        }
        String normalized = raw.trim();
        try {
            return new BusinessDate(parse(normalized));
        } catch (DateTimeParseException exception) {
            throw DomainError.validation("Invalid date format: " + raw);
        }
    }

    private static LocalDate parse(String normalized) {
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(normalized, ISO_DASH);
        }
        if (normalized.matches("\\d{4}/\\d{2}/\\d{2}")) {
            return LocalDate.parse(normalized, ISO_SLASH);
        }
        if (normalized.matches("\\d{2}-\\d{2}-\\d{4}")) {
            return LocalDate.parse(normalized, DAY_FIRST_DASH);
        }
        if (normalized.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return LocalDate.parse(normalized, DAY_FIRST_SLASH);
        }
        throw DomainError.validation("Invalid date format: " + normalized);
    }

    public LocalDate value() {
        return value;
    }

    public String asIso() {
        return value.format(ISO_DASH);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BusinessDate businessDate)) {
            return false;
        }
        return value.equals(businessDate.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
