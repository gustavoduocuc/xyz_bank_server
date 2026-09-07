package com.xyzbank.migration.monthlyinterests.domain;

import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;

public final class Account {

    private final Id id;
    private final String name;
    private final Money balance;
    private final int age;
    private final AccountType type;

    private Account(Id id, String name, Money balance, int age, AccountType type) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.age = age;
        this.type = type;
    }

    public static Account create(String id, String name, double balance, int age, String type) {
        Id accountId = Id.create(id);
        if (name == null || name.trim().isEmpty()) {
            throw DomainError.validation("Account name cannot be empty");
        }
        Money money = Money.create(balance);
        if (money.isNotPositive()) {
            throw DomainError.validation("Account balance must be positive");
        }
        if (isAgeOutsideAllowedRange(age)) {
            throw DomainError.validation("Account age must be between 18 and 100");
        }
        AccountType accountType = AccountType.from(type);
        return new Account(accountId, name.trim(), money, age, accountType);
    }

    public boolean isSenior() {
        int seniorAgeThreshold = 65;
        return age >= seniorAgeThreshold;
    }

    public Id id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money balance() {
        return balance;
    }

    public int age() {
        return age;
    }

    public AccountType type() {
        return type;
    }

    public String idValue() {
        return id.value();
    }

    private static boolean isAgeOutsideAllowedRange(int age) {
        int minimumAge = 18;
        int maximumAge = 100;
        return age < minimumAge || age > maximumAge;
    }
}
