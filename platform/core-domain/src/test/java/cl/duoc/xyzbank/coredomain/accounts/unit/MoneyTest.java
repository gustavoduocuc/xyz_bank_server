package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    /*
     * Cases:
     * 1. Creates with a valid amount and currency
     * 2. Rejects a negative amount
     * 3. Adds two amounts with the same currency
     * 4. Rejects adding amounts with different currencies
     * 5. Two amounts with the same value and currency are equal
     */

    @Test
    void createsWithAValidAmountAndCurrency() {
        Money money = Money.create(new BigDecimal("100.00"), "USD");

        assertEquals(new BigDecimal("100.00"), money.getAmount());
        assertEquals("USD", money.getCurrency());
    }

    @Test
    void rejectsANegativeAmount() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> Money.create(new BigDecimal("-1.00"), "USD"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void addsTwoAmountsWithTheSameCurrency() {
        Money first = Money.create(new BigDecimal("100.00"), "USD");
        Money second = Money.create(new BigDecimal("50.00"), "USD");

        Money total = first.add(second);

        assertEquals(new BigDecimal("150.00"), total.getAmount());
        assertEquals("USD", total.getCurrency());
    }

    @Test
    void rejectsAddingAmountsWithDifferentCurrencies() {
        Money usd = Money.create(new BigDecimal("100.00"), "USD");
        Money clp = Money.create(new BigDecimal("100.00"), "CLP");

        DomainException exception = assertThrows(DomainException.class, () -> usd.add(clp));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    void twoAmountsWithTheSameValueAndCurrencyAreEqual() {
        assertEquals(
                Money.create(new BigDecimal("100.00"), "USD"),
                Money.create(new BigDecimal("100.00"), "USD"));
    }
}
