package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.BusinessDate;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;
import com.xyzbank.migration.shared.domain.SourceLine;

public final class AnnualMovement {

    private final Id accountId;
    private final BusinessDate date;
    private final MovementType type;
    private final Money amount;
    private final String description;
    private final SourceLine sourceLine;

    private AnnualMovement(
            Id accountId,
            BusinessDate date,
            MovementType type,
            Money amount,
            String description,
            SourceLine sourceLine
    ) {
        this.accountId = accountId;
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.sourceLine = sourceLine;
    }

    public static AnnualMovement create(
            String accountId,
            String date,
            String type,
            double amount,
            String description,
            SourceLine sourceLine
    ) {
        Id id = Id.create(accountId);
        BusinessDate businessDate = BusinessDate.create(date);
        MovementType movementType = MovementType.from(type);
        Money money = Money.create(amount);
        if (movementType == MovementType.DEPOSIT && money.isZero()) {
            throw DomainError.validation("Deposit amount cannot be zero");
        }
        String normalizedDescription = description == null ? "" : description.trim();
        return new AnnualMovement(id, businessDate, movementType, money, normalizedDescription, sourceLine);
    }

    public boolean isDeposit() {
        return type == MovementType.DEPOSIT;
    }

    public boolean isOutgoing() {
        return type.isOutgoing();
    }

    public Money absoluteAmount() {
        return amount.absolute();
    }

    public Money depositContribution() {
        if (isDeposit()) {
            return amount;
        }
        return Money.zero();
    }

    public Money withdrawalContribution() {
        if (isOutgoing()) {
            return absoluteAmount();
        }
        return Money.zero();
    }

    public Money netContribution() {
        return amount;
    }

    public Id accountId() {
        return accountId;
    }

    public String accountIdValue() {
        return accountId.value();
    }

    public BusinessDate date() {
        return date;
    }

    public MovementType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public SourceLine sourceLine() {
        return sourceLine;
    }

    public String businessKey() {
        return accountId.value() + "|" + date.asIso() + "|" + type.name() + "|" + amount.amount() + "|" + description;
    }
}
