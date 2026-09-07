package cl.duoc.xyzbank.coredomain.transactions.domain.entities;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class Transaction {

    private final Id id;
    private final Id accountId;
    private final TransactionType type;
    private final Money amount;
    private final LocalDate occurredOn;
    private final String description;

    private Transaction(
            Id id, Id accountId, TransactionType type, Money amount, LocalDate occurredOn, String description) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.occurredOn = occurredOn;
        this.description = description;
    }

    public static Transaction create(
            Id id, Id accountId, TransactionType type, Money amount, LocalDate occurredOn, String description) {
        return new Transaction(id, accountId, type, amount, occurredOn, description);
    }

    public Id getId() {
        return id;
    }

    public Id getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> toPrimitives() {
        Map<String, Object> primitives = new HashMap<>();
        primitives.put("id", id.getValue());
        primitives.put("accountId", accountId.getValue());
        primitives.put("type", type.name());
        primitives.put("amount", amount.toPrimitives());
        primitives.put("occurredOn", occurredOn.toString());
        primitives.put("description", description);
        return primitives;
    }
}
