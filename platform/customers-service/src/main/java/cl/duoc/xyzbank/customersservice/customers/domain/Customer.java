package cl.duoc.xyzbank.customersservice.customers.domain;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A customer's profile. The version is the optimistic-lock token: an update must be
 * made from the version the caller last read, and persistence increments it.
 */
public final class Customer {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String address;
    private final long version;

    private Customer(UUID id, String fullName, String email, String phone, String address, long version) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.version = version;
    }

    public static Customer create(UUID id, String fullName, String email, String phone, String address) {
        if (fullName == null || fullName.isBlank()) {
            throw CustomerException.validation("fullName is required");
        }
        return new Customer(id, fullName.trim(), validEmail(email), phone, address, 0);
    }

    public static Customer restore(
            UUID id, String fullName, String email, String phone, String address, long version) {
        return new Customer(id, fullName, email, phone, address, version);
    }

    public Customer updateContact(String newEmail, String newPhone, String newAddress, long expectedVersion) {
        if (expectedVersion != version) {
            throw CustomerException.versionConflict(
                    "Customer " + id + " is at version " + version + ", not " + expectedVersion);
        }
        return new Customer(
                id,
                fullName,
                newEmail == null ? email : validEmail(newEmail),
                newPhone == null ? phone : newPhone,
                newAddress == null ? address : newAddress,
                version);
    }

    private static String validEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw CustomerException.validation("email must be a valid address");
        }
        return email.trim();
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String address() {
        return address;
    }

    public long version() {
        return version;
    }
}
