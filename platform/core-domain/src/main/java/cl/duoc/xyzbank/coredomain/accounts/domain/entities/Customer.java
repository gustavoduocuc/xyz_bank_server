package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Map;

public final class Customer {

    private final Id id;
    private final String fullName;
    private final String email;

    private Customer(Id id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    public static Customer create(Id id, String fullName, String email) {
        if (fullName == null || fullName.isBlank()) {
            throw DomainException.validation("Full name cannot be empty");
        }
        if (email == null || !email.contains("@")) {
            throw DomainException.validation("Invalid email format");
        }
        return new Customer(id, fullName, email);
    }

    public Id getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "fullName", fullName,
                "email", email);
    }
}
