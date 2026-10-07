package cl.duoc.xyzbank.customersservice.customers.unit;

import cl.duoc.xyzbank.customersservice.customers.application.CustomerResponse;
import cl.duoc.xyzbank.customersservice.customers.application.UpdateCustomerContactRequest;
import cl.duoc.xyzbank.customersservice.customers.application.UpdateCustomerContactUseCase;
import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Update customer contact use case")
class UpdateCustomerContactUseCaseTest {

    /*
     * Cases:
     * 1. Updates email and phone and returns the next version
     * 2. Rejects an update from a stale version and keeps the stored profile
     * 3. Fails with not-found for an unknown id
     * 4. Rejects a request without a version
     */

    private final InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
    private final UpdateCustomerContactUseCase useCase = new UpdateCustomerContactUseCase(repository);
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void seedCustomer() {
        repository.create(Customer.create(id, "Jane Doe", "jane@xyzbank.cl", "+569", "Street 1"), "k-1");
    }

    @Test
    @DisplayName("updates email and phone and returns the next version")
    void updatesEmailAndPhoneAndReturnsTheNextVersion() {
        CustomerResponse response = useCase.execute(
                id.toString(), new UpdateCustomerContactRequest("new@xyzbank.cl", "+56922222222", null, 0L));

        assertEquals("new@xyzbank.cl", response.email());
        assertEquals("+56922222222", response.phone());
        assertEquals("Street 1", response.address());
        assertEquals("Jane Doe", response.fullName());
        assertEquals(1, response.version());
    }

    @Test
    @DisplayName("rejects an update from a stale version and keeps the stored profile")
    void rejectsAnUpdateFromAStaleVersionAndKeepsTheStoredProfile() {
        useCase.execute(id.toString(), new UpdateCustomerContactRequest("first@xyzbank.cl", null, null, 0L));

        CustomerException exception = assertThrows(CustomerException.class, () -> useCase.execute(
                id.toString(), new UpdateCustomerContactRequest("second@xyzbank.cl", null, null, 0L)));

        assertEquals(CustomerException.Type.VERSION_CONFLICT, exception.type());
        assertEquals("first@xyzbank.cl", repository.findById(id).orElseThrow().email());
        assertEquals(1, repository.findById(id).orElseThrow().version());
    }

    @Test
    @DisplayName("fails with not-found for an unknown id")
    void failsWithNotFoundForAnUnknownId() {
        CustomerException exception = assertThrows(CustomerException.class, () -> useCase.execute(
                UUID.randomUUID().toString(), new UpdateCustomerContactRequest("a@xyzbank.cl", null, null, 0L)));

        assertEquals(CustomerException.Type.NOT_FOUND, exception.type());
    }

    @Test
    @DisplayName("rejects a request without a version")
    void rejectsARequestWithoutAVersion() {
        CustomerException exception = assertThrows(CustomerException.class, () -> useCase.execute(
                id.toString(), new UpdateCustomerContactRequest("a@xyzbank.cl", null, null, null)));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
    }
}
