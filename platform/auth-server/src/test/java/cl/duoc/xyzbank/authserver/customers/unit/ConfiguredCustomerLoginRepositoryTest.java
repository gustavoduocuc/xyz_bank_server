package cl.duoc.xyzbank.authserver.customers.unit;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.customers.infrastructure.persistence.ConfiguredCustomerLoginRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ConfiguredCustomerLoginRepository")
class ConfiguredCustomerLoginRepositoryTest {

    /*
     * Cases:
     * 1. Finds the seeded login by its username, with the customer it identifies
     * 2. Knows no username that was not seeded
     * 3. Matches usernames exactly, so a differently-cased username is unknown
     */

    private static final CustomerId SEED_CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");
    private static final CustomerLogin DEMO_LOGIN = CustomerLogin.create("demo", "{noop}demo-password", SEED_CUSTOMER);

    private final ConfiguredCustomerLoginRepository repository =
            new ConfiguredCustomerLoginRepository(List.of(DEMO_LOGIN));

    @Test
    @DisplayName("finds the seeded login by its username, with the customer it identifies")
    void findsTheSeededLoginByItsUsername() {
        Optional<CustomerLogin> login = repository.findByUsername("demo");

        assertEquals(Optional.of(DEMO_LOGIN), login);
        assertEquals(SEED_CUSTOMER, login.orElseThrow().customerId());
    }

    @Test
    @DisplayName("knows no username that was not seeded")
    void knowsNoUsernameThatWasNotSeeded() {
        Optional<CustomerLogin> login = repository.findByUsername("mallory");

        assertTrue(login.isEmpty());
    }

    @Test
    @DisplayName("matches usernames exactly, treating a differently-cased username as unknown")
    void matchesUsernamesExactly() {
        Optional<CustomerLogin> login = repository.findByUsername("DEMO");

        assertTrue(login.isEmpty());
    }
}
