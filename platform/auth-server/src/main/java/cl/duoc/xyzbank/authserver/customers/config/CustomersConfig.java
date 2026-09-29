package cl.duoc.xyzbank.authserver.customers.config;

import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.customers.infrastructure.adapters.CustomerLoginUserDetailsService;
import cl.duoc.xyzbank.authserver.customers.infrastructure.persistence.ConfiguredCustomerLoginRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.List;

@Configuration
public class CustomersConfig {

    @Bean
    public CustomerLoginRepository customerLoginRepository(DemoCustomerProperties demoCustomer) {
        String passwordHash =
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(demoCustomer.password());
        return new ConfiguredCustomerLoginRepository(List.of(CustomerLogin.create(
                demoCustomer.username(), passwordHash, CustomerId.create(demoCustomer.customerId()))));
    }

    @Bean
    public UserDetailsService userDetailsService(CustomerLoginRepository customerLoginRepository) {
        return new CustomerLoginUserDetailsService(customerLoginRepository);
    }
}
