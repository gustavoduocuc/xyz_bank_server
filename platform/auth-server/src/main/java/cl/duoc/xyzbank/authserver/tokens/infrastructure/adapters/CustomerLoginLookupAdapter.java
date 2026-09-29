package cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;

import java.util.Optional;

public class CustomerLoginLookupAdapter implements CustomerIdLookup {

    private final CustomerLoginRepository customerLogins;

    public CustomerLoginLookupAdapter(CustomerLoginRepository customerLogins) {
        this.customerLogins = customerLogins;
    }

    @Override
    public Optional<String> customerIdOf(String username) {
        return customerLogins.findByUsername(username).map(login -> login.customerId().toPrimitives());
    }
}
