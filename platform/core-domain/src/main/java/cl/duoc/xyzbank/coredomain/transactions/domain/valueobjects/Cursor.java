package cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;

import java.util.Objects;

public final class Cursor {

    private final String value;

    private Cursor(String value) {
        this.value = value;
    }

    public static Cursor create(String value) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation("Cursor cannot be empty");
        }
        return new Cursor(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Cursor other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
