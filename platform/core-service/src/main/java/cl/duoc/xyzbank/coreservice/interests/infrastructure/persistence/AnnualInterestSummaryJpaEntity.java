package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "annual_interest_summaries")
public class AnnualInterestSummaryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private int year;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false)
    private BigDecimal closingBalance;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "interest_amount", nullable = false)
    private BigDecimal interestAmount;

    @Column(nullable = false)
    private String currency;

    protected AnnualInterestSummaryJpaEntity() {
    }

    public AnnualInterestSummaryJpaEntity(
            UUID id,
            UUID accountId,
            int year,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal interestRate,
            BigDecimal interestAmount,
            String currency) {
        this.id = id;
        this.accountId = accountId;
        this.year = year;
        this.openingBalance = openingBalance;
        this.closingBalance = closingBalance;
        this.interestRate = interestRate;
        this.interestAmount = interestAmount;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public String getCurrency() {
        return currency;
    }
}
