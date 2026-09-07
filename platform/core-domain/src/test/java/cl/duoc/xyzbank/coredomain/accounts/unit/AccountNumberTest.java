package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The AccountNumber")
class AccountNumberTest {

    /*
     * Cases:
     * 1. Accepts a well-formed 10-digit account number
     * 2. Rejects a null value
     * 3. Rejects a blank value
     * 4. Rejects a value with letters
     * 5. Rejects a value with the wrong length
     * 6. Two account numbers with the same value are equal
     */

    @Test
    @DisplayName("accepts a well-formed 10-digit account number")
    void acceptsAWellFormedTenDigitAccountNumber() {
        AccountNumber accountNumber = AccountNumber.create("1234567890");

        assertEquals("1234567890", accountNumber.getValue());
    }

    @Test
    @DisplayName("rejects a null value")
    void rejectsANullValue() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create(null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank value")
    void rejectsABlankValue() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a value with letters")
    void rejectsAValueWithLetters() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("12345abcde"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a value with the wrong length")
    void rejectsAValueWithTheWrongLength() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("123"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("considers two account numbers with the same value equal")
    void twoAccountNumbersWithTheSameValueAreEqual() {
        assertEquals(AccountNumber.create("1234567890"), AccountNumber.create("1234567890"));
    }
}
