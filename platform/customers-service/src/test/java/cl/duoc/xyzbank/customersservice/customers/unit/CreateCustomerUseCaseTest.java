package cl.duoc.xyzbank.customersservice.customers.unit;

import cl.duoc.xyzbank.customersservice.customers.application.CreateCustomerRequest;
import cl.duoc.xyzbank.customersservice.customers.application.CreateCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.application.CustomerResponse;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Create customer use case")
class CreateCustomerUseCaseTest {

    /*
     * Cases:
     * 1. Creates a customer at version 0 under the idempotency key
     * 2. Returns the first customer when the same key is used again
     * 3. Rejects a request without an idempotency key
     * 4. Rejects an invalid body without creating anything
     */

    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
    private final CreateCustomerUseCase useCase = new CreateCustomerUseCase(repository);
    private final CreateCustomerRequest request =
            new CreateCustomerRequest("Jane Doe", "jane@xyzbank.cl", "+56911111111", "Street 1");

    @Test
    @DisplayName("creates a customer at version 0 under the idempotency key")
    void createsACustomerAtVersion0UnderTheIdempotencyKey() {
        CustomerResponse response = useCase.execute("k-1", request);

        assertEquals("Jane Doe", response.fullName());
        assertEquals("jane@xyzbank.cl", response.email());
        assertEquals(0, response.version());
        assertEquals(1, repository.size());
    }

    @Test
    @DisplayName("returns the first customer when the same key is used again")
    void returnsTheFirstCustomerWhenTheSameKeyIsUsedAgain() {
        CustomerResponse first = useCase.execute("k-1", request);

        CustomerResponse retry = useCase.execute("k-1", request);

        assertEquals(first.id(), retry.id());
        assertEquals(1, repository.size());
    }

    @Test
    @DisplayName("rejects a request without an idempotency key")
    void rejectsARequestWithoutAnIdempotencyKey() {
        CustomerException exception = assertThrows(CustomerException.class, () -> useCase.execute(" ", request));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
        assertEquals(0, repository.size());
    }

    @Test
    @DisplayName("rejects an invalid body without creating anything")
    void rejectsAnInvalidBodyWithoutCreatingAnything() {
        CreateCustomerRequest invalid = new CreateCustomerRequest("Jane Doe", "not-an-email", null, null);

        assertThrows(CustomerException.class, () -> useCase.execute("k-2", invalid));

        assertEquals(0, repository.size());
    }
}
