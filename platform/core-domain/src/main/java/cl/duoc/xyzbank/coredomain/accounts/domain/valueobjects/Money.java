package cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class Money {

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money create(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw DomainException.validation("Amount cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw DomainException.validation("Currency cannot be empty");
        }
        return new Money(amount, currency);
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw DomainException.validation("Cannot add money with different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    public Map<String, Object> toPrimitives() {
        return Map.of("amount", amount, "currency", currency);
    }
}
