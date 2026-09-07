package com.xyzbank.migration.monthlyinterests.domain;

import com.xyzbank.migration.shared.domain.Money;

public final class InterestApplied {

    private final Account account;
    private final double rate;
    private final Money finalBalance;

    public InterestApplied(Account account, double rate, Money finalBalance) {
        this.account = account;
        this.rate = rate;
        this.finalBalance = finalBalance;
    }

    public Account account() {
        return account;
    }

    public double rate() {
        return rate;
    }

    public Money finalBalance() {
        return finalBalance;
    }

    public String accountIdValue() {
        return account.idValue();
    }

    public String accountName() {
        return account.name();
    }

    public AccountType accountType() {
        return account.type();
    }

    public int accountAge() {
        return account.age();
    }

    public double previousBalanceValue() {
        return account.balance().amount();
    }

    public double finalBalanceValue() {
        return finalBalance.amount();
    }
}
