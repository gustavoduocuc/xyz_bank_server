package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void acceptsAWellFormedTenDigitAccountNumber() {
        AccountNumber accountNumber = AccountNumber.create("1234567890");

        assertEquals("1234567890", accountNumber.getValue());
    }

    @Test
    void rejectsANullValue() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create(null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void rejectsABlankValue() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void rejectsAValueWithLetters() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("12345abcde"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void rejectsAValueWithTheWrongLength() {
        DomainException exception = assertThrows(DomainException.class, () -> AccountNumber.create("123"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void twoAccountNumbersWithTheSameValueAreEqual() {
        assertEquals(AccountNumber.create("1234567890"), AccountNumber.create("1234567890"));
    }
}
