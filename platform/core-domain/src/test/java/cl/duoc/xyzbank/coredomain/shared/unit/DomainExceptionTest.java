package cl.duoc.xyzbank.coredomain.shared.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The DomainException")
class DomainExceptionTest {

    /*
     * Cases:
     * 1. notFound() sets type NOT_FOUND and preserves the message
     * 2. validation() sets type VALIDATION and preserves the message
     * 3. create() sets type OTHER and preserves the message
     */

    @Test
    @DisplayName("marks a not-found error with type NOT_FOUND and preserves the message")
    void notFoundSetsTypeNotFound() {
        DomainException exception = DomainException.notFound("Customer not found");

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertEquals("Customer not found", exception.getMessage());
    }

    @Test
    @DisplayName("marks a validation error with type VALIDATION and preserves the message")
    void validationSetsTypeValidation() {
        DomainException exception = DomainException.validation("Id cannot be empty");

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals("Id cannot be empty", exception.getMessage());
    }

    @Test
    @DisplayName("marks a generic error with type OTHER and preserves the message")
    void createSetsTypeOther() {
        DomainException exception = DomainException.create("Unexpected domain error");

        assertEquals(DomainException.Type.OTHER, exception.getType());
        assertEquals("Unexpected domain error", exception.getMessage());
    }
}
