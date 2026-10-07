package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The Account")
class AccountTest {

    /*
     * Cases:
     * 1. Creates with a valid account number, owner id, balance, and currency
     * 2. Exposes its balance as a Money value object
     * 3. Withdraw reduces the balance when within the balance and the daily limit
     * 4. Withdraw allows withdrawing exactly the full balance
     * 5. Withdraw rejects an amount greater than the current balance
     * 6. Withdraw rejects a single withdrawal that exceeds the daily limit
     * 7. Withdraw rejects a further withdrawal that would push the day's cumulative total over the limit
     * 8. Withdraw allows cumulative withdrawals that stay within the daily limit
     * 9. Withdraw resets the cumulative daily total on a new calendar day
     * 10. Withdraw rejects an amount whose currency does not match the account's balance currency
     * 11. Credit increases the balance by the credited amount
     * 12. Credit rejects an amount whose currency does not match the account's balance currency
     * 13. Opens with a zero balance, ACTIVE, version 0 and an optional alias
     * 14. Updates the alias and its own daily limit, remembering the command key
     * 15. Rejects a non-positive daily limit or one in another currency
     * 16. Rejects an update or closure made from a stale version
     * 17. Closes an account with a zero balance
     * 18. Rejects closing an account with funds or one already closed
     * 19. A closed account accepts no withdrawal, credit or update
     * 20. Uses its own daily limit when set and the default otherwise
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

    @Test
    @DisplayName("withdraw allows withdrawing exactly the full balance")
    void withdrawAllowsWithdrawingExactlyTheFullBalance() {
        Money balance = Money.create(new BigDecimal("50.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("50.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit);

        assertEquals(new BigDecimal("0.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects a single withdrawal that exceeds the daily limit")
    void withdrawRejectsASingleWithdrawalThatExceedsTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("1500.00"), "USD");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("5000.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects a further withdrawal that would push the day's cumulative total over the limit")
    void withdrawRejectsAFurtherWithdrawalThatWouldPushTheDaysCumulativeTotalOverTheLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        LocalDate today = LocalDate.of(2026, 1, 1);
        account.withdraw(Money.create(new BigDecimal("700.00"), "USD"), today, dailyLimit);

        DomainException exception = assertThrows(DomainException.class, () -> account.withdraw(
                Money.create(new BigDecimal("400.00"), "USD"), today, dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("4300.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw allows cumulative withdrawals that stay within the daily limit")
    void withdrawAllowsCumulativeWithdrawalsThatStayWithinTheDailyLimit() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        LocalDate today = LocalDate.of(2026, 1, 1);
        account.withdraw(Money.create(new BigDecimal("700.00"), "USD"), today, dailyLimit);

        account.withdraw(Money.create(new BigDecimal("300.00"), "USD"), today, dailyLimit);

        assertEquals(new BigDecimal("4000.00"), account.getBalance().getAmount());
        assertEquals(new BigDecimal("1000.00"), account.getDailyWithdrawnAmount().getAmount());
    }

    @Test
    @DisplayName("withdraw resets the cumulative daily total on a new calendar day")
    void withdrawResetsTheCumulativeDailyTotalOnANewCalendarDay() {
        Money balance = Money.create(new BigDecimal("5000.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");
        account.withdraw(Money.create(new BigDecimal("1000.00"), "USD"), LocalDate.of(2026, 1, 1), dailyLimit);

        account.withdraw(Money.create(new BigDecimal("900.00"), "USD"), LocalDate.of(2026, 1, 2), dailyLimit);

        assertEquals(new BigDecimal("900.00"), account.getDailyWithdrawnAmount().getAmount());
        assertEquals(new BigDecimal("3100.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("withdraw rejects an amount whose currency does not match the account's balance currency")
    void withdrawRejectsAnAmountWhoseCurrencyDoesNotMatchTheAccountsBalanceCurrency() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("100.00"), "CLP");
        Money dailyLimit = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(
                DomainException.class, () -> account.withdraw(amount, LocalDate.of(2026, 1, 1), dailyLimit));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("credit increases the balance by the credited amount")
    void creditIncreasesTheBalanceByTheCreditedAmount() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("35.00"), "USD");

        account.credit(amount);

        assertEquals(new BigDecimal("535.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("credit rejects an amount whose currency does not match the account's balance currency")
    void creditRejectsAnAmountWhoseCurrencyDoesNotMatchTheAccountsBalanceCurrency() {
        Money balance = Money.create(new BigDecimal("500.00"), "USD");
        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);
        Money amount = Money.create(new BigDecimal("35.00"), "CLP");

        DomainException exception = assertThrows(DomainException.class, () -> account.credit(amount));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("opens with a zero balance, ACTIVE, version 0 and an optional alias")
    void opensWithAZeroBalanceActiveVersion0AndAnOptionalAlias() {
        Account account = openAccount("Ahorro");

        assertEquals(usd("0.00"), account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());
        assertEquals(Optional.of("Ahorro"), account.getAlias());
        assertEquals(Optional.empty(), account.getDailyWithdrawalLimit());
    }

    @Test
    @DisplayName("updates the alias and its own daily limit, remembering the command key")
    void updatesTheAliasAndItsOwnDailyLimitRememberingTheCommandKey() {
        Account account = openAccount(null);

        account.updateDetails("Viajes", usd("800.00"), 0, "upd-1");

        assertEquals(Optional.of("Viajes"), account.getAlias());
        assertEquals(Optional.of(usd("800.00")), account.getDailyWithdrawalLimit());
        assertTrue(account.isLastCommand("upd-1"));
        assertFalse(account.isLastCommand("upd-2"));
    }

    @Test
    @DisplayName("rejects a non-positive daily limit or one in another currency")
    void rejectsANonPositiveDailyLimitOrOneInAnotherCurrency() {
        Account account = openAccount(null);

        DomainException zero = assertThrows(DomainException.class,
                () -> account.updateDetails(null, usd("0.00"), 0, "upd-1"));
        DomainException otherCurrency = assertThrows(DomainException.class,
                () -> account.updateDetails(null, Money.create(new BigDecimal("100.00"), "CLP"), 0, "upd-2"));

        assertEquals(DomainException.Type.VALIDATION, zero.getType());
        assertEquals(DomainException.Type.VALIDATION, otherCurrency.getType());
    }

    @Test
    @DisplayName("rejects an update or closure made from a stale version")
    void rejectsAnUpdateOrClosureMadeFromAStaleVersion() {
        Account account = openAccount(null);

        DomainException update = assertThrows(DomainException.class,
                () -> account.updateDetails("Viajes", null, 3, "upd-1"));
        DomainException closure = assertThrows(DomainException.class, () -> account.close(3, "close-1"));

        assertEquals(DomainException.Type.CONFLICT, update.getType());
        assertEquals(DomainException.Type.CONFLICT, closure.getType());
    }

    @Test
    @DisplayName("closes an account with a zero balance")
    void closesAnAccountWithAZeroBalance() {
        Account account = openAccount(null);

        account.close(0, "close-1");

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        assertTrue(account.isLastCommand("close-1"));
    }

    @Test
    @DisplayName("rejects closing an account with funds or one already closed")
    void rejectsClosingAnAccountWithFundsOrOneAlreadyClosed() {
        Account withFunds = openAccount(null);
        withFunds.credit(usd("10.00"));
        Account closed = openAccount(null);
        closed.close(0, "close-1");

        DomainException funds = assertThrows(DomainException.class, () -> withFunds.close(0, "close-2"));
        DomainException again = assertThrows(DomainException.class, () -> closed.close(0, "close-3"));

        assertEquals(DomainException.Type.CONFLICT, funds.getType());
        assertEquals(AccountStatus.ACTIVE, withFunds.getStatus());
        assertEquals(DomainException.Type.CONFLICT, again.getType());
    }

    @Test
    @DisplayName("a closed account accepts no withdrawal, credit or update")
    void aClosedAccountAcceptsNoWithdrawalCreditOrUpdate() {
        Account account = openAccount(null);
        account.close(0, "close-1");

        DomainException withdrawal = assertThrows(DomainException.class,
                () -> account.withdraw(usd("1.00"), LocalDate.of(2025, 1, 1), usd("100.00")));
        DomainException credit = assertThrows(DomainException.class, () -> account.credit(usd("1.00")));
        DomainException update = assertThrows(DomainException.class,
                () -> account.updateDetails("Viajes", null, 0, "upd-1"));

        assertEquals(DomainException.Type.CONFLICT, withdrawal.getType());
        assertEquals(DomainException.Type.CONFLICT, credit.getType());
        assertEquals(DomainException.Type.CONFLICT, update.getType());
        assertEquals(usd("0.00"), account.getBalance());
    }

    @Test
    @DisplayName("uses its own daily limit when set and the default otherwise")
    void usesItsOwnDailyLimitWhenSetAndTheDefaultOtherwise() {
        Account account = openAccount(null);

        assertEquals(usd("5000.00"), account.effectiveDailyLimit(usd("5000.00")));
        account.updateDetails(null, usd("800.00"), 0, "upd-1");
        assertEquals(usd("800.00"), account.effectiveDailyLimit(usd("5000.00")));
    }

    private static Account openAccount(String alias) {
        return Account.open(Id.generate(), AccountNumber.create("1234567890"), Id.generate(), "USD", alias);
    }

    private static Money usd(String amount) {
        return Money.create(new BigDecimal(amount), "USD");
    }
}
