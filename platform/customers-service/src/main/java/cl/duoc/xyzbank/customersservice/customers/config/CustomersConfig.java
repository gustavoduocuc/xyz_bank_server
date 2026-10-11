package cl.duoc.xyzbank.customersservice.customers.config;

import cl.duoc.xyzbank.customersservice.customers.application.CreateCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.application.GetCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.application.UpdateCustomerContactUseCase;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomersConfig {

    @Bean
    public GetCustomerUseCase getCustomerUseCase(CustomerRepository customerRepository) {
        return new GetCustomerUseCase(customerRepository);
    }

    @Bean
    public CreateCustomerUseCase createCustomerUseCase(CustomerRepository customerRepository) {
        return new CreateCustomerUseCase(customerRepository);
    }

    @Bean
    public UpdateCustomerContactUseCase updateCustomerContactUseCase(CustomerRepository customerRepository) {
        return new UpdateCustomerContactUseCase(customerRepository);
    }
}
