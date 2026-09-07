package cl.duoc.xyzbank.coredomain.accounts.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Optional;

public interface CustomerRepository {
    void save(Customer customer);

    Optional<Customer> findById(Id id);
}
