package cl.duoc.xyzbank.customersservice.customers.integration;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;
import cl.duoc.xyzbank.customersservice.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The JPA customer repository")
class JpaCustomerRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Finds the demo customer seeded by the migrations
     * 2. Keeps its table and Flyway history in the customers schema
     * 3. Creates a customer that can be found by id and by idempotency key
     * 4. Returns the first customer when the idempotency key is already taken
     * 5. Increments the version on update
     * 6. Rejects an update made from a stale version
     * 7. Keeps the idempotency key through an update
     */

    private static final UUID demoCustomer = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("finds the demo customer seeded by the migrations")
    void findsTheDemoCustomerSeededByTheMigrations() {
        Customer demo = repository.findById(demoCustomer).orElseThrow();

        assertEquals("Ana Perez", demo.fullName());
        assertEquals("ana.perez@xyzbank.cl", demo.email());
    }

    @Test
    @DisplayName("keeps its table and Flyway history in the customers schema")
    void keepsItsTableAndFlywayHistoryInTheCustomersSchema() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'customers' AND table_name IN ('customers', 'flyway_schema_history')
                """, Integer.class);

        assertEquals(2, tables);
    }

    @Test
    @DisplayName("creates a customer that can be found by id and by idempotency key")
    void createsACustomerThatCanBeFoundByIdAndByIdempotencyKey() {
        String key = "key-" + UUID.randomUUID();
        Customer created = repository.create(newCustomer(), key);

        assertEquals(0, created.version());
        assertEquals("jane@xyzbank.cl", repository.findById(created.id()).orElseThrow().email());
        assertEquals(created.id(), repository.findByIdempotencyKey(key).orElseThrow().id());
    }

    @Test
    @DisplayName("returns the first customer when the idempotency key is already taken")
    void returnsTheFirstCustomerWhenTheIdempotencyKeyIsAlreadyTaken() {
        String key = "key-" + UUID.randomUUID();
        Customer first = repository.create(newCustomer(), key);

        Customer second = repository.create(newCustomer(), key);

        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("increments the version on update")
    void incrementsTheVersionOnUpdate() {
        Customer created = repository.create(newCustomer(), "key-" + UUID.randomUUID());

        Customer updated = repository.update(created.updateContact("new@xyzbank.cl", null, null, 0));

        assertEquals(1, updated.version());
        assertEquals("new@xyzbank.cl", repository.findById(created.id()).orElseThrow().email());
    }

    @Test
    @DisplayName("rejects an update made from a stale version")
    void rejectsAnUpdateMadeFromAStaleVersion() {
        Customer created = repository.create(newCustomer(), "key-" + UUID.randomUUID());
        repository.update(created.updateContact("first@xyzbank.cl", null, null, 0));

        CustomerException exception = assertThrows(CustomerException.class,
                () -> repository.update(created.updateContact("second@xyzbank.cl", null, null, 0)));

        assertEquals(CustomerException.Type.VERSION_CONFLICT, exception.type());
        assertEquals("first@xyzbank.cl", repository.findById(created.id()).orElseThrow().email());
    }

    @Test
    @DisplayName("keeps the idempotency key through an update")
    void keepsTheIdempotencyKeyThroughAnUpdate() {
        String idempotencyKey = "key-" + UUID.randomUUID();
        Customer created = repository.create(newCustomer(), idempotencyKey);

        repository.update(created.updateContact("new@xyzbank.cl", null, null, 0));

        assertEquals(created.id(), repository.findByIdempotencyKey(idempotencyKey).orElseThrow().id());
    }

    private static Customer newCustomer() {
        return Customer.create(UUID.randomUUID(), "Jane Doe", "jane@xyzbank.cl", "+569", "Street 1");
    }
}
