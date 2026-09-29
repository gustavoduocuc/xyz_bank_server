package cl.duoc.xyzbank.authserver.customers.domain.valueobjects;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;

import java.util.Objects;
import java.util.Optional;
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
        String trimmed = value.trim();
        return canonicalUuidOf(trimmed)
                .filter(canonical -> canonical.equalsIgnoreCase(trimmed))
                .map(CustomerId::new)
                .orElseThrow(() -> DomainException.validation("Customer id must be a UUID: " + value));
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

    private static Optional<String> canonicalUuidOf(String value) {
        try {
            return Optional.of(UUID.fromString(value).toString());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
