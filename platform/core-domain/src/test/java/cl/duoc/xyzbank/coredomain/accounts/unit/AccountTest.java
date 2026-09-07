package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Account")
class AccountTest {

    /*
     * Cases:
     * 1. Creates with a valid account number, owner id, balance, and currency
     * 2. Exposes its balance as a Money value object
     * 3. Withdraw reduces the balance when within the balance and the daily limit
     */

    @Test
    @DisplayName("creates with a valid account number, owner id, balance, and currency")
    void createsWithAValidAccountNumberOwnerIdBalanceAndCurrency() {
        Id id = Id.generate();
        Id customerId = Id.generate();
        AccountNumber accountNumber = AccountNumber.create("1234567890");
        Money balance = Money.create(new BigDecimal("100.00"), "USD");

        Account account = Account.create(id, accountNumber, customerId, balance);

        assertEquals(id, account.getId());
        assertEquals(accountNumber, account.getAccountNumber());
        assertEquals(customerId, account.getCustomerId());
        assertEquals(balance, account.getBalance());
    }

    @Test
    @DisplayName("exposes its balance as a Money value object")
    void exposesItsBalanceAsAMoneyValueObject() {
        Money balance = Money.create(new BigDecimal("250.50"), "CLP");

        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);

        assertEquals(new BigDecimal("250.50"), account.getBalance().getAmount());
        assertEquals("CLP", account.getBalance().getCurrency());
    }

    @Test
    @DisplayName("withdraw reduces the balance when within the balance and the daily limit")
    void withdrawReducesTheBalanceWhenWithinTheBalanceAndTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit);

        assertEquals(new BigDecimal("400.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects an amount greater than the current balance")
    void withdrawRejectsAnAmountGreaterThanTheCurrentBalance() {
        Money balance = Money.create(new BigDecimal("50.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("50.00"), account.getBalance().getAmount());
    }
}
