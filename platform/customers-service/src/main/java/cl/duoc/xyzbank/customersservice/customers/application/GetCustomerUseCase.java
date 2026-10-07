package cl.duoc.xyzbank.customersservice.customers.application;

import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;

public class GetCustomerUseCase {

    private final CustomerRepository customerRepository;

    public GetCustomerUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse execute(String customerId) {
        return customerRepository.findById(CustomerIds.parse(customerId))
                .map(CustomerResponse::from)
                .orElseThrow(() -> CustomerException.notFound("Customer " + customerId + " not found"));
    }
}
