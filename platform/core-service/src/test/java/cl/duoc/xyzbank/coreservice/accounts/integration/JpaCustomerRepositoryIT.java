package cl.duoc.xyzbank.coreservice.accounts.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence.JpaCustomerRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JpaCustomerRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Saves a customer and finds it by id
     * 2. Returns empty when the customer id does not exist
     */

    @Autowired
    private JpaCustomerRepository customerRepository;

    @Test
    void savesACustomerAndFindsItById() {
        Id id = Id.generate();
        Customer customer = Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl");

        customerRepository.save(customer);
        Optional<Customer> found = customerRepository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("Jane Doe", found.get().getFullName());
        assertEquals("jane.doe@xyzbank.cl", found.get().getEmail());
    }

    @Test
    void returnsEmptyWhenTheCustomerIdDoesNotExist() {
        Optional<Customer> found = customerRepository.findById(Id.generate());

        assertTrue(found.isEmpty());
    }
}
