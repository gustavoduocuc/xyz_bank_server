package cl.duoc.xyzbank.customersservice.customers.application;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerRepository;

import java.util.UUID;

/** Repeating an Idempotency-Key returns the customer the first request created. */
public class CreateCustomerUseCase {

    private final CustomerRepository customerRepository;

    public CreateCustomerUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse execute(String idempotencyKey, CreateCustomerRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw CustomerException.validation("Idempotency-Key header is required");
        }
        return customerRepository.findByIdempotencyKey(idempotencyKey)
                .map(CustomerResponse::from)
                .orElseGet(() -> create(idempotencyKey, request));
    }

    private CustomerResponse create(String idempotencyKey, CreateCustomerRequest request) {
        Customer customer = Customer.create(
                UUID.randomUUID(), request.fullName(), request.email(), request.phone(), request.address());
        return CustomerResponse.from(customerRepository.create(customer, idempotencyKey));
    }
}
