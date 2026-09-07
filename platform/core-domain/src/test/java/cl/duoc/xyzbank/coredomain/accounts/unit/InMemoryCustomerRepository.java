package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();

    public InMemoryCustomerRepository() {
    }

    public InMemoryCustomerRepository(List<Customer> initialCustomers) {
        initialCustomers.forEach(this::save);
    }

    @Override
    public void save(Customer customer) {
        customers.put(customer.getId().getValue(), customer);
    }

    @Override
    public Optional<Customer> findById(Id id) {
        return Optional.ofNullable(customers.get(id.getValue()));
    }
}
