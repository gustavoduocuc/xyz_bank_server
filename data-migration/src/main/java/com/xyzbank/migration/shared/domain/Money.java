package com.xyzbank.migration.shared.domain;

import java.util.Objects;

public final class Money {

    private final double amount;

    private Money(double amount) {
        this.amount = amount;
    }

    public static Money create(double amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(0);
    }

    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money multiply(double factor) {
        return new Money(this.amount * factor);
    }

    public Money absolute() {
        return new Money(Math.abs(amount));
    }

    public boolean exceeds(double threshold) {
        return amount > threshold;
    }

    public double amount() {
        return amount;
    }

    public boolean isPositive() {
        return amount > 0;
    }

    public boolean isNotPositive() {
        return amount <= 0;
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isNegative() {
        return amount < 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Money money)) {
            return false;
        }
        return Double.compare(money.amount, amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }
}
