package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountTest {

    /*
     * Cases:
     * 1. Creates with a valid account number, owner id, balance, and currency
     * 2. Exposes its balance as a Money value object
     */

    @Test
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
    void exposesItsBalanceAsAMoneyValueObject() {
        Money balance = Money.create(new BigDecimal("250.50"), "CLP");

        Account account = Account.create(
                Id.generate(), AccountNumber.create("1234567890"), Id.generate(), balance);

        assertEquals(new BigDecimal("250.50"), account.getBalance().getAmount());
        assertEquals("CLP", account.getBalance().getCurrency());
    }
}
