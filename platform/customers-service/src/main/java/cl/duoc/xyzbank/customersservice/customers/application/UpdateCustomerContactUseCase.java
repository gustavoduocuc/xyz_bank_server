package cl.duoc.xyzbank.customersservice.customers.application;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;

public class UpdateCustomerContactUseCase {

    private final CustomerRepository customerRepository;

    public UpdateCustomerContactUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse execute(String customerId, UpdateCustomerContactRequest request) {
        if (request.version() == null) {
            throw CustomerException.validation("version is required");
        }
        Customer customer = customerRepository.findById(CustomerIds.parse(customerId))
                .orElseThrow(() -> CustomerException.notFound("Customer " + customerId + " not found"));
        Customer updated = customer.updateContact(
                request.email(), request.phone(), request.address(), request.version());
        return CustomerResponse.from(customerRepository.update(updated));
    }
}
