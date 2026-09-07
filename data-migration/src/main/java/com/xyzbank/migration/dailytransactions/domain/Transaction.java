package com.xyzbank.migration.dailytransactions.domain;

import com.xyzbank.migration.shared.domain.BusinessDate;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;

import java.util.Map;

public final class Transaction {

    private final Id id;
    private final BusinessDate date;
    private final Money amount;
    private final TransactionType type;

    private Transaction(Id id, BusinessDate date, Money amount, TransactionType type) {
        this.id = id;
        this.date = date;
        this.amount = amount;
        this.type = type;
    }

    public static Transaction create(String id, String date, double amount, String type) {
        Id transactionId = Id.create(id);
        BusinessDate businessDate = BusinessDate.create(date);
        Money money = Money.create(amount);
        if (money.isNotPositive()) {
            throw DomainError.validation("Transaction amount must be positive");
        }
        TransactionType transactionType = TransactionType.from(type);
        return new Transaction(transactionId, businessDate, money, transactionType);
    }

    public boolean exceedsAmount(double threshold) {
        return amount.exceeds(threshold);
    }

    public Id id() {
        return id;
    }

    public BusinessDate date() {
        return date;
    }

    public Money amount() {
        return amount;
    }

    public TransactionType type() {
        return type;
    }

    public String businessKey() {
        return date.asIso() + "|" + amount.amount() + "|" + type.name();
    }

    public Map<String, Object> toPrimitives() {
        return Map.of(
                "id", id.value(),
                "date", date.asIso(),
                "amount", amount.amount(),
                "type", type.name()
        );
    }
}
