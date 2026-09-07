package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;

public class GetCustomerProfileUseCase {

    private final CustomerRepository customerRepository;

    public GetCustomerProfileUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerProfileResponse execute(String customerId) {
        Id id = Id.create(customerId);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Customer " + customerId + " not found"));

        return toResponse(customer);
    }

    private CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getId().getValue(),
                customer.getFullName(),
                customer.getEmail());
    }
}
