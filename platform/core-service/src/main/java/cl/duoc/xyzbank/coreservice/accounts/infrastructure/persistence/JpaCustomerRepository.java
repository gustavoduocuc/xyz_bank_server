package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaCustomerRepository implements CustomerRepository {

    private final SpringDataCustomerRepository jpaRepository;

    public JpaCustomerRepository(SpringDataCustomerRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Customer customer) {
        jpaRepository.save(toEntity(customer));
    }

    @Override
    public Optional<Customer> findById(Id id) {
        return jpaRepository.findById(UUID.fromString(id.getValue()))
                .map(this::toDomain);
    }

    private CustomerJpaEntity toEntity(Customer customer) {
        return new CustomerJpaEntity(
                UUID.fromString(customer.getId().getValue()),
                customer.getFullName(),
                customer.getEmail());
    }

    private Customer toDomain(CustomerJpaEntity entity) {
        return Customer.create(
                Id.create(entity.getId().toString()),
                entity.getFullName(),
                entity.getEmail());
    }
}
