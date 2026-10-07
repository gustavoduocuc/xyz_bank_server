package cl.duoc.xyzbank.coredomain.accounts.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.DailyWithdrawalUsage;
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
    private DailyWithdrawalUsage dailyWithdrawalUsage;
    private AccountStatus status;
    private String alias;
    private Money dailyWithdrawalLimit;
    private String lastCommandIdempotencyKey;

    private Account(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage,
            AccountLifecycle lifecycle) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.balance = balance;
        this.version = version;
        this.dailyWithdrawalUsage = dailyWithdrawalUsage;
        this.status = lifecycle.status();
        this.alias = lifecycle.alias();
        this.dailyWithdrawalLimit = lifecycle.dailyWithdrawalLimit();
        this.lastCommandIdempotencyKey = lifecycle.lastCommandIdempotencyKey();
    }

    /**
     * The lifecycle state of an account as stored: its status, its alias, its own daily
     * withdrawal limit (null when the configured default applies) and the idempotency key of
     * the last update or closure applied to it.
     */
    public record AccountLifecycle(
            AccountStatus status, String alias, Money dailyWithdrawalLimit, String lastCommandIdempotencyKey) {

        public static AccountLifecycle active() {
            return new AccountLifecycle(AccountStatus.ACTIVE, null, null, null);
        }
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage,
            AccountLifecycle lifecycle) {
        return new Account(id, accountNumber, customerId, balance, version, dailyWithdrawalUsage, lifecycle);
    }

    public static Account create(
            Id id,
            AccountNumber accountNumber,
            Id customerId,
            Money balance,
            long version,
            DailyWithdrawalUsage dailyWithdrawalUsage) {
        return create(id, accountNumber, customerId, balance, version, dailyWithdrawalUsage, AccountLifecycle.active());
    }

    /** A new, empty, ACTIVE account. */
    public static Account open(Id id, AccountNumber accountNumber, Id customerId, String currency, String alias) {
        Money zero = Money.create(BigDecimal.ZERO.setScale(2), currency);
        return create(id, accountNumber, customerId, zero, 0L, DailyWithdrawalUsage.none(currency),
                new AccountLifecycle(AccountStatus.ACTIVE, alias, null, null));
    }

    public static Account create(Id id, AccountNumber accountNumber, Id customerId, Money balance) {
        return create(id, accountNumber, customerId, balance, 0L, DailyWithdrawalUsage.none(balance.getCurrency()));
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
        return dailyWithdrawalUsage.getWithdrawnAmount();
    }

    public Optional<LocalDate> getDailyWithdrawnDate() {
        return dailyWithdrawalUsage.getDate();
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    public Optional<Money> getDailyWithdrawalLimit() {
        return Optional.ofNullable(dailyWithdrawalLimit);
    }

    public Optional<String> getLastCommandIdempotencyKey() {
        return Optional.ofNullable(lastCommandIdempotencyKey);
    }

    /** True when this key is the one of the last update or closure applied: a retry of it. */
    public boolean isLastCommand(String idempotencyKey) {
        return idempotencyKey != null && idempotencyKey.equals(lastCommandIdempotencyKey);
    }

    public Money effectiveDailyLimit(Money defaultLimit) {
        return dailyWithdrawalLimit == null ? defaultLimit : dailyWithdrawalLimit;
    }

    /** Changes the alias and/or the account's own daily limit; a null argument keeps the current value. */
    public void updateDetails(String newAlias, Money newDailyLimit, long expectedVersion, String idempotencyKey) {
        requireActive();
        requireVersion(expectedVersion);
        if (newDailyLimit != null) {
            if (!newDailyLimit.getCurrency().equals(balance.getCurrency())) {
                throw DomainException.validation("The daily limit must be in " + balance.getCurrency());
            }
            if (newDailyLimit.getAmount().signum() <= 0) {
                throw DomainException.validation("The daily limit must be positive");
            }
            this.dailyWithdrawalLimit = newDailyLimit;
        }
        if (newAlias != null) {
            this.alias = newAlias;
        }
        this.lastCommandIdempotencyKey = idempotencyKey;
    }

    public void close(long expectedVersion, String idempotencyKey) {
        requireActive();
        requireVersion(expectedVersion);
        if (balance.getAmount().signum() != 0) {
            throw DomainException.conflict("Account " + id.getValue() + " can only be closed with a zero balance");
        }
        this.status = AccountStatus.CLOSED;
        this.lastCommandIdempotencyKey = idempotencyKey;
    }

    public void withdraw(Money amount, LocalDate today, Money dailyLimit) {
        requireActive();
        Money newBalance = this.balance.subtract(amount);
        this.dailyWithdrawalUsage = this.dailyWithdrawalUsage.recordWithdrawal(amount, today, dailyLimit);
        this.balance = newBalance;
    }

    public void credit(Money amount) {
        requireActive();
        this.balance = this.balance.add(amount);
    }

    private void requireActive() {
        if (status == AccountStatus.CLOSED) {
            throw DomainException.conflict("Account " + id.getValue() + " is CLOSED");
        }
    }

    private void requireVersion(long expectedVersion) {
        if (expectedVersion != version) {
            throw DomainException.conflict(
                    "Account " + id.getValue() + " is at version " + version + ", not " + expectedVersion);
        }
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountNumber", accountNumber.getValue(),
                "customerId", customerId.getValue(),
                "balance", balance.toPrimitives());
    }
}
