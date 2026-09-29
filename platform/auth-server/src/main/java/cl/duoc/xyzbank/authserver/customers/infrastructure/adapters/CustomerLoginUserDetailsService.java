package cl.duoc.xyzbank.authserver.customers.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Lets Spring Security's form login authenticate against customer logins. The failure is
 * the same whether the username is unknown or the password wrong: Spring Security turns
 * both into one generic "bad credentials" error on the login page.
 */
public class CustomerLoginUserDetailsService implements UserDetailsService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final CustomerLoginRepository customerLogins;

    public CustomerLoginUserDetailsService(CustomerLoginRepository customerLogins) {
        this.customerLogins = customerLogins;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return customerLogins
                .findByUsername(username)
                .map(login -> User.withUsername(login.username())
                        .password(login.passwordHash())
                        .roles(CUSTOMER_ROLE)
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username"));
    }
}
