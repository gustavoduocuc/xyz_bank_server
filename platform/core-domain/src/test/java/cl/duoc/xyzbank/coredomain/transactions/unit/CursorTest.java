package cl.duoc.xyzbank.coredomain.transactions.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Cursor")
class CursorTest {

    /*
     * Cases:
     * 1. Creates from a non-blank value
     * 2. Rejects a null value
     * 3. Rejects a blank value
     * 4. Two cursors with the same value are equal
     */

    @Test
    @DisplayName("creates from a non-blank value")
    void createsFromANonBlankValue() {
        Cursor cursor = Cursor.create("b3BhcXVl");

        assertEquals("b3BhcXVl", cursor.getValue());
    }

    @Test
    @DisplayName("rejects a null value")
    void rejectsANullValue() {
        DomainException exception = assertThrows(DomainException.class, () -> Cursor.create(null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank value")
    void rejectsABlankValue() {
        DomainException exception = assertThrows(DomainException.class, () -> Cursor.create("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("considers two cursors with the same value equal")
    void twoCursorsWithTheSameValueAreEqual() {
        assertEquals(Cursor.create("b3BhcXVl"), Cursor.create("b3BhcXVl"));
    }
}
