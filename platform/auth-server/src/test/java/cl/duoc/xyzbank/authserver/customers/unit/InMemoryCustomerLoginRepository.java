package cl.duoc.xyzbank.authserver.customers.unit;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCustomerLoginRepository implements CustomerLoginRepository {

    private final Map<String, CustomerLogin> logins = new ConcurrentHashMap<>();

    public InMemoryCustomerLoginRepository(CustomerLogin... logins) {
        for (CustomerLogin login : logins) {
            this.logins.put(login.username(), login);
        }
    }

    @Override
    public Optional<CustomerLogin> findByUsername(String username) {
        return Optional.ofNullable(logins.get(username));
    }
}
