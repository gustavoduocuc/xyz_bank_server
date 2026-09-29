package cl.duoc.xyzbank.authserver.customers.domain.repositories;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;

import java.util.Optional;

public interface CustomerLoginRepository {

    Optional<CustomerLogin> findByUsername(String username);
}
