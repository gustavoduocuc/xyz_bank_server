package cl.duoc.xyzbank.coredomain.interests.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.math.BigDecimal;
import java.util.Map;

public final class AnnualInterestSummary {

    private final Id id;
    private final Id accountId;
    private final int year;
    private final Money openingBalance;
    private final Money closingBalance;
    private final BigDecimal interestRate;
    private final Money interestAmount;

    private AnnualInterestSummary(
            Id id,
            Id accountId,
            int year,
            Money openingBalance,
            Money closingBalance,
            BigDecimal interestRate,
            Money interestAmount) {
        this.id = id;
        this.accountId = accountId;
        this.year = year;
        this.openingBalance = openingBalance;
        this.closingBalance = closingBalance;
        this.interestRate = interestRate;
        this.interestAmount = interestAmount;
    }

    public static AnnualInterestSummary create(
            Id id,
            Id accountId,
            int year,
            Money openingBalance,
            Money closingBalance,
            BigDecimal interestRate,
            Money interestAmount) {
        if (interestRate == null || interestRate.signum() < 0) {
            throw DomainException.validation("Interest rate cannot be negative");
        }
        return new AnnualInterestSummary(id, accountId, year, openingBalance, closingBalance, interestRate, interestAmount);
    }

    public Id getId() {
        return id;
    }

    public Id getAccountId() {
        return accountId;
    }

    public int getYear() {
        return year;
    }

    public Money getOpeningBalance() {
        return openingBalance;
    }

    public Money getClosingBalance() {
        return closingBalance;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public Money getInterestAmount() {
        return interestAmount;
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.getValue(),
                "accountId", accountId.getValue(),
                "year", year,
                "openingBalance", openingBalance.toPrimitives(),
                "closingBalance", closingBalance.toPrimitives(),
                "interestRate", interestRate,
                "interestAmount", interestAmount.toPrimitives());
    }
}
