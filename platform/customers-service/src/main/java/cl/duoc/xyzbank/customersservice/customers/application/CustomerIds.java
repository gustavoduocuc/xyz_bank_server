package cl.duoc.xyzbank.customersservice.customers.application;

import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;

import java.util.UUID;

final class CustomerIds {

    private CustomerIds() {
    }

    static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw CustomerException.validation("Customer id must be a UUID: " + id);
        }
    }
}
