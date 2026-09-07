package cl.duoc.xyzbank.coreservice.transactions.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    private String description;

    protected TransactionJpaEntity() {
    }

    public TransactionJpaEntity(
            UUID id,
            UUID accountId,
            TransactionType type,
            BigDecimal amount,
            String currency,
            LocalDate occurredOn,
            String description) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.occurredOn = occurredOn;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getDescription() {
        return description;
    }
}
