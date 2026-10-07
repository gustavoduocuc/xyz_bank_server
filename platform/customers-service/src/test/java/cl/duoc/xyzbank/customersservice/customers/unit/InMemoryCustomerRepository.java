package cl.duoc.xyzbank.customersservice.customers.unit;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<UUID, Customer> customers = new HashMap<>();
    private final Map<String, UUID> idsByKey = new HashMap<>();

    @Override
    public Optional<Customer> findById(UUID id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public Optional<Customer> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(idsByKey.get(idempotencyKey)).map(customers::get);
    }

    @Override
    public Customer create(Customer customer, String idempotencyKey) {
        UUID existing = idsByKey.putIfAbsent(idempotencyKey, customer.id());
        if (existing != null) {
            return customers.get(existing);
        }
        customers.put(customer.id(), customer);
        return customer;
    }

    @Override
    public Customer update(Customer customer) {
        Customer stored = customers.get(customer.id());
        if (stored.version() != customer.version()) {
            throw CustomerException.versionConflict("stale version");
        }
        Customer saved = Customer.restore(customer.id(), customer.fullName(), customer.email(),
                customer.phone(), customer.address(), customer.version() + 1);
        customers.put(saved.id(), saved);
        return saved;
    }

    public int size() {
        return customers.size();
    }
}
