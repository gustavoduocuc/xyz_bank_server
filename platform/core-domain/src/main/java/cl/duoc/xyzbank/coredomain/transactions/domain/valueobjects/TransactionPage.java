package cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects;

import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;

import java.util.List;
import java.util.Optional;

public final class TransactionPage {

    private final List<Transaction> items;
    private final Optional<Cursor> nextCursor;

    private TransactionPage(List<Transaction> items, Optional<Cursor> nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public static TransactionPage create(List<Transaction> items, Optional<Cursor> nextCursor) {
        return new TransactionPage(items, nextCursor);
    }

    public List<Transaction> getItems() {
        return items;
    }

    public Optional<Cursor> getNextCursor() {
        return nextCursor;
    }
}
