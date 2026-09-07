package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Map;

public final class Account {

    private final Id id;
    private final AccountNumber accountNumber;
    private final Id customerId;
    private final Money balance;

    private Account(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
    }

    public static Account create(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        return new Account(id, accountNumber, customerId, balance);
    }

    public Id getId() {
        return id;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

    public Id getCustomerId() {
        return customerId;
    }

    public Money getBalance() {
        return balance;
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountNumber", accountNumber.getValue(),
                "customerId", customerId.getValue(),
                "balance", balance.toPrimitives());
    }
}
