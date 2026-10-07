package cl.duoc.xyzbank.customersservice.customers.unit;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Customer")
class CustomerTest {

    /*
     * Cases:
     * 1. Creates with a full name, email, phone and address at version 0
     * 2. Rejects a blank full name
     * 3. Rejects a malformed email
     * 4. Updates only the contact details that are given
     * 5. Rejects a malformed email on update
     * 6. Rejects an update made from a stale version
     */

    private static final UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("creates with a full name, email, phone and address at version 0")
    void createsWithAFullNameEmailPhoneAndAddressAtVersion0() {
        Customer customer = Customer.create(id, "Jane Doe", "jane@xyzbank.cl", "+56911111111", "Av. Siempre Viva 1");

        assertEquals(id, customer.id());
        assertEquals("Jane Doe", customer.fullName());
        assertEquals("jane@xyzbank.cl", customer.email());
        assertEquals("+56911111111", customer.phone());
        assertEquals("Av. Siempre Viva 1", customer.address());
        assertEquals(0, customer.version());
    }

    @Test
    @DisplayName("rejects a blank full name")
    void rejectsABlankFullName() {
        CustomerException exception = assertThrows(
                CustomerException.class, () -> Customer.create(id, " ", "jane@xyzbank.cl", null, null));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
    }

    @Test
    @DisplayName("rejects a malformed email")
    void rejectsAMalformedEmail() {
        CustomerException exception = assertThrows(
                CustomerException.class, () -> Customer.create(id, "Jane Doe", "not-an-email", null, null));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
    }

    @Test
    @DisplayName("updates only the contact details that are given")
    void updatesOnlyTheContactDetailsThatAreGiven() {
        Customer customer = Customer.create(id, "Jane Doe", "jane@xyzbank.cl", "+56911111111", "Old street 1");

        Customer updated = customer.updateContact("jane.doe@xyzbank.cl", null, "New street 2", 0);

        assertEquals("Jane Doe", updated.fullName());
        assertEquals("jane.doe@xyzbank.cl", updated.email());
        assertEquals("+56911111111", updated.phone());
        assertEquals("New street 2", updated.address());
    }

    @Test
    @DisplayName("rejects a malformed email on update")
    void rejectsAMalformedEmailOnUpdate() {
        Customer customer = Customer.create(id, "Jane Doe", "jane@xyzbank.cl", null, null);

        CustomerException exception = assertThrows(
                CustomerException.class, () -> customer.updateContact("broken", null, null, 0));

        assertEquals(CustomerException.Type.VALIDATION, exception.type());
    }

    @Test
    @DisplayName("rejects an update made from a stale version")
    void rejectsAnUpdateMadeFromAStaleVersion() {
        Customer customer = Customer.restore(id, "Jane Doe", "jane@xyzbank.cl", null, null, 1);

        CustomerException exception = assertThrows(
                CustomerException.class, () -> customer.updateContact("new@xyzbank.cl", null, null, 0));

        assertEquals(CustomerException.Type.VERSION_CONFLICT, exception.type());
    }
}
