package cl.duoc.xyzbank.authserver.customers.domain.entities;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;

/**
 * The credentials a customer logs in with, identified by username, and the customer they
 * authenticate as. The username is only a login handle; tokens identify the customer by
 * CustomerId (design.md Decision 4).
 */
public final class CustomerLogin {

    private final String username;
    private final String passwordHash;
    private final CustomerId customerId;

    private CustomerLogin(String username, String passwordHash, CustomerId customerId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.customerId = customerId;
    }

    public static CustomerLogin create(String username, String passwordHash, CustomerId customerId) {
        if (username == null || username.isBlank()) {
            throw DomainException.validation("Username cannot be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw DomainException.validation("Password hash cannot be blank");
        }
        if (customerId == null) {
            throw DomainException.validation("A login must identify a customer");
        }
        return new CustomerLogin(username, passwordHash, customerId);
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
