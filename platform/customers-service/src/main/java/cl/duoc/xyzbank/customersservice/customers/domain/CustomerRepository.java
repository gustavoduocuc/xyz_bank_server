package cl.duoc.xyzbank.customersservice.customers.domain;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByIdempotencyKey(String idempotencyKey);

    /** Creates the customer under the key; if the key is already taken, returns that key's customer. */
    Customer create(Customer customer, String idempotencyKey);

    /** Saves a change made from the customer's version; returns it with the incremented version. */
    Customer update(Customer customer);
}
