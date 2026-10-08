package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;

import java.util.UUID;

final class Uuids {

    private Uuids() {
    }

    /** Parses an id given by the caller; a malformed one is a validation error. Null stays null. */
    static UUID parse(String name, String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw PaymentException.validation(name + " must be a UUID");
        }
    }
}
