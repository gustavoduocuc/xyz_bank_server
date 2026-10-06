package cl.duoc.xyzbank.authserver.customers.unit;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The CustomerId")
class CustomerIdTest {

    /*
     * Cases:
     * 1. Accepts a UUID
     * 2. Rejects a blank value
     * 3. Rejects a value that is not a UUID
     * 4. Two ids with the same value are equal
     * 5. Rejects an abbreviated UUID that Java's parser would tolerate
     * 6. Ignores whitespace surrounding the UUID
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

    @Test
    @DisplayName("accepts a UUID")
    void acceptsAUuid() {
        CustomerId customerId = CustomerId.create(SEED_CUSTOMER);

        assertEquals(SEED_CUSTOMER, customerId.toPrimitives());
    }

    @Test
    @DisplayName("rejects a blank value")
    void rejectsABlankValue() {
        DomainException exception = assertThrows(DomainException.class, () -> CustomerId.create(" "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a value that is not a UUID")
    void rejectsAValueThatIsNotAUuid() {
        DomainException exception = assertThrows(DomainException.class, () -> CustomerId.create("customer-42"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("considers two ids with the same value equal")
    void considersTwoIdsWithTheSameValueEqual() {
        assertEquals(CustomerId.create(SEED_CUSTOMER), CustomerId.create(SEED_CUSTOMER));
    }

    @Test
    @DisplayName("rejects an abbreviated UUID")
    void rejectsAnAbbreviatedUuid() {
        DomainException exception = assertThrows(DomainException.class, () -> CustomerId.create("1-1-1-1-1"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("ignores whitespace surrounding the UUID")
    void ignoresWhitespaceSurroundingTheUuid() {
        CustomerId customerId = CustomerId.create("  " + SEED_CUSTOMER + " ");

        assertEquals(CustomerId.create(SEED_CUSTOMER), customerId);
    }
}
