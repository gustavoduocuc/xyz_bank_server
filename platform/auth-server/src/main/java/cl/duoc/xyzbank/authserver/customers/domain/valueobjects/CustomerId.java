package cl.duoc.xyzbank.authserver.customers.domain.valueobjects;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * The bank's customer identifier, which becomes the "sub" of every token. Must be a UUID
 * because that is how core-service identifies customers.
 */
public final class CustomerId {

    private final String value;

    private CustomerId(String value) {
        this.value = value;
    }

    public static CustomerId create(String value) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation("Customer id cannot be blank");
        }
        String canonical = canonicalUuid(value.trim());
        if (!canonical.equalsIgnoreCase(value.trim())) {
            throw DomainException.validation("Customer id must be a UUID: " + value);
        }
        return new CustomerId(canonical);
    }

    private static String canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    public String toPrimitives() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CustomerId customerId && value.equals(customerId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
