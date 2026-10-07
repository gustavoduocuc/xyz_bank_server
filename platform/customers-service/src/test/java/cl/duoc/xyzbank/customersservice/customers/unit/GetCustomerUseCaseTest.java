package cl.duoc.xyzbank.customersservice.customers.unit;

import cl.duoc.xyzbank.customersservice.customers.application.CustomerResponse;
import cl.duoc.xyzbank.customersservice.customers.application.GetCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Get customer use case")
class GetCustomerUseCaseTest {

    /*
     * Cases:
     * 1. Returns the profile of an existing customer
     * 2. Fails with not-found for an unknown id
     * 3. Fails with a validation error for a malformed id
     */

    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
    private final GetCustomerUseCase useCase = new GetCustomerUseCase(repository);

    @Test
    @DisplayName("returns the profile of an existing customer")
    void returnsTheProfileOfAnExistingCustomer() {
        UUID id = UUID.randomUUID();
        repository.create(Customer.create(id, "Jane Doe", "jane@xyzbank.cl", "+569", "Street 1"), "k-1");

        CustomerResponse response = useCase.execute(id.toString());

        assertEquals(new CustomerResponse(id.toString(), "Jane Doe", "jane@xyzbank.cl", "+569", "Street 1", 0),
                response);
    }

    @Test
    @DisplayName("fails with not-found for an unknown id")
    void failsWithNotFoundForAnUnknownId() {
        CustomerException exception = assertThrows(
                CustomerException.class, () -> useCase.execute(UUID.randomUUID().toString()));

        assertEquals(CustomerException.Type.NOT_FOUND, exception.type());
    }

    @Test
    @DisplayName("fails with a validation error for a malformed id")
    void failsWithAValidationErrorForAMalformedId() {
        CustomerException exception = assertThrows(CustomerException.class, () -> useCase.execute("not-a-uuid"));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
    }
}
