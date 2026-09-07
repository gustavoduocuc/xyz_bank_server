package cl.duoc.xyzbank.coredomain.shared.domain;

import java.util.Objects;
import java.util.UUID;

public final class Id {

    private final String value;

    private Id(String value) {
        this.value = value;
    }

    public static Id create(String value) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation("Id cannot be empty");
        }
        return new Id(value);
    }

    public static Id generate() {
        return new Id(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Id other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    public String toPrimitives() {
        return value;
    }
}
