package cl.duoc.xyzbank.customersservice.customers.application;

/** {@code version} is the one the caller last read; omitted contact fields keep their value. */
public record UpdateCustomerContactRequest(String email, String phone, String address, Long version) {
}
