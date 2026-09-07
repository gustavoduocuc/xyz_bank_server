package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryCustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CustomerProfileResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetCustomerProfileUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetCustomerProfileUseCaseTest {

    /*
     * Cases:
     * 1. Returns the profile of an existing customer
     * 2. Throws not found for an unknown customer id
     * 3. Throws validation for a malformed customer id
     */

    @Test
    void returnsTheProfileOfAnExistingCustomer() {
        Id id = Id.generate();
        Customer customer = Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl");
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.save(customer);
        GetCustomerProfileUseCase useCase = new GetCustomerProfileUseCase(repository);

        CustomerProfileResponse response = useCase.execute(id.getValue());

        assertEquals(id.getValue(), response.id());
        assertEquals("Jane Doe", response.fullName());
        assertEquals("jane.doe@xyzbank.cl", response.email());
    }

    @Test
    void throwsNotFoundForAnUnknownCustomerId() {
        GetCustomerProfileUseCase useCase = new GetCustomerProfileUseCase(new InMemoryCustomerRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    void throwsValidationForAMalformedCustomerId() {
        GetCustomerProfileUseCase useCase = new GetCustomerProfileUseCase(new InMemoryCustomerRepository());

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
