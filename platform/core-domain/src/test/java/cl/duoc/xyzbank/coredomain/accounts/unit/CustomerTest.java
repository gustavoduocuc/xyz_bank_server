package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Customer")
class CustomerTest {

    /*
     * Cases:
     * 1. Creates with a valid full name and email
     * 2. Rejects a blank full name
     * 3. Rejects an invalid email
     * 4. Rejects a null email
     */

    @Test
    @DisplayName("creates with a valid full name and email")
    void createsWithAValidFullNameAndEmail() {
        Id id = Id.generate();

        Customer customer = Customer.create(id, "Jane Doe", "jane.doe@xyzbank.cl");

        assertEquals(id, customer.getId());
        assertEquals("Jane Doe", customer.getFullName());
        assertEquals("jane.doe@xyzbank.cl", customer.getEmail());
    }

    @Test
    @DisplayName("rejects a blank full name")
    void rejectsABlankFullName() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Customer.create(Id.generate(), "  ", "jane.doe@xyzbank.cl"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects an invalid email")
    void rejectsAnInvalidEmail() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Customer.create(Id.generate(), "Jane Doe", "not-an-email"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a null email")
    void rejectsANullEmail() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Customer.create(Id.generate(), "Jane Doe", null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
