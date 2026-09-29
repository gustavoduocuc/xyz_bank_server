package cl.duoc.xyzbank.authserver.customers.infrastructure.persistence;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;

import java.util.List;
import java.util.Optional;

/**
 * The customer logins seeded from configuration (just the demo customer); user management
 * is out of scope, so there is no persistent store behind this.
 */
public class ConfiguredCustomerLoginRepository implements CustomerLoginRepository {

    private final List<CustomerLogin> logins;

    public ConfiguredCustomerLoginRepository(List<CustomerLogin> logins) {
        this.logins = List.copyOf(logins);
    }

    @Override
    public Optional<CustomerLogin> findByUsername(String username) {
        return logins.stream().filter(login -> login.username().equals(username)).findFirst();
    }
}
