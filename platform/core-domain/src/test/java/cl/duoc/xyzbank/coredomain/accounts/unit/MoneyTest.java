package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Money")
class MoneyTest {

    /*
     * Cases:
     * 1. Creates with a valid amount and currency
     * 2. Rejects a negative amount
     * 3. Rejects a blank currency
     * 4. Adds two amounts with the same currency
     * 5. Rejects adding amounts with different currencies
     * 6. Two amounts with the same value and currency are equal
     * 7. Subtracts two amounts with the same currency
     * 8. Rejects subtracting amounts with different currencies
     * 9. Rejects a subtraction that would produce a negative amount
     */

    @Test
    @DisplayName("creates with a valid amount and currency")
    void createsWithAValidAmountAndCurrency() {
        Money money = Money.create(new BigDecimal("100.00"), "USD");

        assertEquals(new BigDecimal("100.00"), money.getAmount());
        assertEquals("USD", money.getCurrency());
    }

    @Test
    @DisplayName("rejects a negative amount")
    void rejectsANegativeAmount() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Money.create(new BigDecimal("-1.00"), "USD"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank currency")
    void rejectsABlankCurrency() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Money.create(new BigDecimal("100.00"), "  "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("adds two amounts with the same currency")
    void addsTwoAmountsWithTheSameCurrency() {
        Money first = Money.create(new BigDecimal("100.00"), "USD");
        Money second = Money.create(new BigDecimal("50.00"), "USD");

        Money total = first.add(second);

        assertEquals(new BigDecimal("150.00"), total.getAmount());
        assertEquals("USD", total.getCurrency());
    }

    @Test
    @DisplayName("rejects adding amounts with different currencies")
    void rejectsAddingAmountsWithDifferentCurrencies() {
        Money usd = Money.create(new BigDecimal("100.00"), "USD");
        Money clp = Money.create(new BigDecimal("100.00"), "CLP");

        DomainException exception = assertThrows(DomainException.class, () -> usd.add(clp));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("considers two amounts with the same value and currency equal")
    void twoAmountsWithTheSameValueAndCurrencyAreEqual() {
        assertEquals(
                Money.create(new BigDecimal("100.00"), "USD"),
                Money.create(new BigDecimal("100.00"), "USD"));
    }

    @Test
    @DisplayName("subtracts two amounts with the same currency")
    void subtractsTwoAmountsWithTheSameCurrency() {
        Money first = Money.create(new BigDecimal("100.00"), "USD");
        Money second = Money.create(new BigDecimal("40.00"), "USD");

        Money result = first.subtract(second);

        assertEquals(new BigDecimal("60.00"), result.getAmount());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    @DisplayName("rejects subtracting amounts with different currencies")
    void rejectsSubtractingAmountsWithDifferentCurrencies() {
        Money usd = Money.create(new BigDecimal("100.00"), "USD");
        Money clp = Money.create(new BigDecimal("50.00"), "CLP");

        DomainException exception = assertThrows(DomainException.class, () -> usd.subtract(clp));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a subtraction that would produce a negative amount")
    void rejectsASubtractionThatWouldProduceANegativeAmount() {
        Money balance = Money.create(new BigDecimal("50.00"), "USD");
        Money tooMuch = Money.create(new BigDecimal("100.00"), "USD");

        DomainException exception = assertThrows(DomainException.class, () -> balance.subtract(tooMuch));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
