package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public final class Account {

    private final Id id;
    private final AccountNumber accountNumber;
    private final Id customerId;
    private Money balance;
    private final long version;
    private Money dailyWithdrawnAmount;
    private Optional<LocalDate> dailyWithdrawnDate;

    private Account(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            Money dailyWithdrawnAmount,
            Optional<LocalDate> dailyWithdrawnDate) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.version = version;
        this.dailyWithdrawnAmount = dailyWithdrawnAmount;
        this.dailyWithdrawnDate = dailyWithdrawnDate;
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            Money dailyWithdrawnAmount,
            Optional<LocalDate> dailyWithdrawnDate) {
        return new Account(id, accountNumber, customerId, balance, version, dailyWithdrawnAmount, dailyWithdrawnDate);
    }

    public static Account create(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        return create(
                id,
                accountNumber,
                customerId,
                balance,
                0L,
                Money.create(BigDecimal.ZERO, balance.getCurrency()),
                Optional.empty());
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

    public long getVersion() {
        return version;
    }

    public Money getDailyWithdrawnAmount() {
        return dailyWithdrawnAmount;
    }

    public Optional<LocalDate> getDailyWithdrawnDate() {
        return dailyWithdrawnDate;
    }

    public void withdraw(Money amount, LocalDate today, Money dailyLimit) {
        Money newBalance = this.balance.subtract(amount);
        Money cumulativeToday = this.dailyWithdrawnAmount.add(amount);
        if (cumulativeToday.getAmount().compareTo(dailyLimit.getAmount()) > 0) {
            throw DomainException.validation("Daily withdrawal limit exceeded");
        }
        this.balance = newBalance;
        this.dailyWithdrawnAmount = cumulativeToday;
        this.dailyWithdrawnDate = Optional.of(today);
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountNumber", accountNumber.getValue(),
                "customerId", customerId.getValue(),
                "balance", balance.toPrimitives());
    }
}
