package cl.duoc.xyzbank.authserver.customers.unit;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.customers.infrastructure.adapters.CustomerLoginUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The CustomerLoginUserDetailsService")
class CustomerLoginUserDetailsServiceTest {

    /*
     * Cases:
     * 1. Resolves a known username with its password hash
     * 2. Grants a known username the customer role
     * 3. Refuses an unknown username
     */

    private final CustomerLoginUserDetailsService service = new CustomerLoginUserDetailsService(
            new InMemoryCustomerLoginRepository(CustomerLogin.create(
                    "demo", "{noop}demo-password", CustomerId.create("11111111-1111-1111-1111-111111111111"))));

    @Test
    @DisplayName("resolves a known username with its password hash")
    void resolvesAKnownUsernameWithItsPasswordHash() {
        UserDetails user = service.loadUserByUsername("demo");

        assertEquals("demo", user.getUsername());
        assertEquals("{noop}demo-password", user.getPassword());
    }

    @Test
    @DisplayName("grants a known username the customer role")
    void grantsAKnownUsernameTheCustomerRole() {
        UserDetails user = service.loadUserByUsername("demo");

        assertEquals(
                Set.of("ROLE_CUSTOMER"),
                AuthorityUtils.authorityListToSet(user.getAuthorities()));
    }

    @Test
    @DisplayName("refuses an unknown username")
    void refusesAnUnknownUsername() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("mallory"));
    }
}
