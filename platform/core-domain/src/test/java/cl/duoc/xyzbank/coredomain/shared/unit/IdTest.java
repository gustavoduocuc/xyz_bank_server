package cl.duoc.xyzbank.coredomain.shared.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Id")
class IdTest {

    /*
     * Cases:
     * 1. Generates a non-blank value
     * 2. Creates from a non-blank value
     * 3. Rejects a null value
     * 4. Rejects a blank value
     * 5. Two ids with the same value are equal
     * 6. Two ids with different values are not equal
     */

    @Test
    @DisplayName("generates a non-blank value")
    void generatesANonBlankValue() {
        Id id = Id.generate();

        assertFalse(id.getValue().isBlank());
    }

    @Test
    @DisplayName("creates from a non-blank value")
    void createsFromANonBlankValue() {
        Id id = Id.create("customer-123");

        assertEquals("customer-123", id.getValue());
    }

    @Test
    @DisplayName("rejects a null value")
    void rejectsANullValue() {
        DomainException exception = assertThrows(DomainException.class, () -> Id.create(null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank value")
    void rejectsABlankValue() {
        DomainException exception = assertThrows(DomainException.class, () -> Id.create("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("considers two ids with the same value equal")
    void twoIdsWithTheSameValueAreEqual() {
        assertEquals(Id.create("customer-123"), Id.create("customer-123"));
    }

    @Test
    @DisplayName("considers two ids with different values not equal")
    void twoIdsWithDifferentValuesAreNotEqual() {
        assertNotEquals(Id.create("customer-123"), Id.create("customer-456"));
    }
}
