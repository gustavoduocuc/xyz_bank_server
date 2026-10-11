package cl.duoc.xyzbank.customersservice.customers.infrastructure.persistence;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Each write runs in its own short transaction (Spring Data's), so a losing duplicate insert
 * can re-read the winning row afterwards.
 */
@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository springData;

    public JpaCustomerRepository(SpringDataCustomerRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return springData.findById(id).map(JpaCustomerRepository::toDomain);
    }

    @Override
    public Optional<Customer> findByIdempotencyKey(String idempotencyKey) {
        return springData.findByIdempotencyKey(idempotencyKey).map(JpaCustomerRepository::toDomain);
    }

    @Override
    public Customer create(Customer customer, String idempotencyKey) {
        try {
            return toDomain(springData.saveAndFlush(toNewEntity(customer, idempotencyKey)));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return findByIdempotencyKey(idempotencyKey).orElseThrow(() -> concurrentDuplicate);
        }
    }

    @Override
    public Customer update(Customer customer) {
        try {
            return toDomain(springData.saveAndFlush(toChangedEntity(customer)));
        } catch (ObjectOptimisticLockingFailureException staleVersion) {
            throw CustomerException.versionConflict("Customer " + customer.id() + " was changed concurrently");
        }
    }

    private static CustomerJpaEntity toNewEntity(Customer customer, String idempotencyKey) {
        return new CustomerJpaEntity(customer.id(), customer.fullName(), customer.email(),
                customer.phone(), customer.address(), null, idempotencyKey);
    }

    // idempotency_key is not updatable, so a change leaves the key stored at creation untouched
    private static CustomerJpaEntity toChangedEntity(Customer customer) {
        return new CustomerJpaEntity(customer.id(), customer.fullName(), customer.email(),
                customer.phone(), customer.address(), customer.version(), null);
    }

    private static Customer toDomain(CustomerJpaEntity entity) {
        return Customer.restore(entity.getId(), entity.getFullName(), entity.getEmail(),
                entity.getPhone(), entity.getAddress(), entity.getVersion());
    }
}
