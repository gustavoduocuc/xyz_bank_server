package cl.duoc.xyzbank.coredomain.shared.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainExceptionTest {

    /*
     * Cases:
     * 1. notFound() sets type NOT_FOUND and preserves the message
     * 2. validation() sets type VALIDATION and preserves the message
     * 3. create() sets type OTHER and preserves the message
     */

    @Test
    void notFoundSetsTypeNotFound() {
        DomainException exception = DomainException.notFound("Customer not found");

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    void validationSetsTypeValidation() {
        DomainException exception = DomainException.validation("Id cannot be empty");

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Id cannot be empty", exception.getMessage());
    }

    @Test
    void createSetsTypeOther() {
        DomainException exception = DomainException.create("Unexpected domain error");

        assertEquals(DomainException.Type.OTHER, exception.getType());
        assertEquals("Unexpected domain error", exception.getMessage());
    }
}
