package com.xyzbank.migration.shared.domain;

import java.util.Objects;

public final class Id {

    private final String value;

    private Id(String value) {
        this.value = value;
    }

    public static Id create(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw DomainError.validation("Id cannot be empty");
        }
        return new Id(value.trim());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Id id)) {
            return false;
        }
        return value.equals(id.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
