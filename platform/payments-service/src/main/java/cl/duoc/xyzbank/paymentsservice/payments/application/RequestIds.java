package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;

import java.util.Optional;
import java.util.UUID;

/** Ids given by the caller: a malformed one is a validation error, not a technical one. */
final class RequestIds {

    private RequestIds() {
    }

    static UUID required(String field, String value) {
        return optional(field, value).orElseThrow(() -> PaymentException.validation(field + " is required"));
    }

    static Optional<UUID> optional(String field, String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            throw PaymentException.validation(field + " must be a UUID");
        }
    }
}
