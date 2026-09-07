package cl.duoc.xyzbank.coredomain.transactions.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionPage;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();

    @Override
    public void save(Transaction transaction) {
        transactions.put(transaction.getId().getValue(), transaction);
    }

    @Override
    public Optional<Transaction> findById(Id id) {
        return Optional.ofNullable(transactions.get(id.getValue()));
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return transactions.values().stream()
                .filter(transaction -> transaction.getIdempotencyKey().equals(Optional.of(idempotencyKey)))
                .findFirst();
    }

    @Override
    public TransactionPage findByAccountId(
            Id accountId, DateRange dateRange, Optional<TransactionType> type, Optional<Cursor> cursor, int pageSize) {
        List<Transaction> matching = transactions.values().stream()
                .filter(transaction -> transaction.getAccountId().equals(accountId))
                .filter(transaction -> dateRange.getFrom()
                        .map(from -> !transaction.getOccurredOn().isBefore(from))
                        .orElse(true))
                .filter(transaction -> dateRange.getTo()
                        .map(to -> !transaction.getOccurredOn().isAfter(to))
                        .orElse(true))
                .filter(transaction -> type.map(t -> transaction.getType() == t).orElse(true))
                .sorted(Comparator.comparing(Transaction::getOccurredOn)
                        .thenComparing(transaction -> transaction.getId().getValue())
                        .reversed())
                .toList();

        List<Transaction> window = afterCursor(matching, cursor);
        List<Transaction> pageItems = window.size() > pageSize ? window.subList(0, pageSize) : window;
        Optional<Cursor> nextCursor = window.size() > pageSize
                ? Optional.of(Cursor.create(positionKey(pageItems.get(pageItems.size() - 1))))
                : Optional.empty();

        return TransactionPage.create(pageItems, nextCursor);
    }

    private List<Transaction> afterCursor(List<Transaction> matching, Optional<Cursor> cursor) {
        if (cursor.isEmpty()) {
            return matching;
        }
        String key = cursor.get().getValue();
        int index = -1;
        for (int i = 0; i < matching.size(); i++) {
            if (positionKey(matching.get(i)).equals(key)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw DomainException.validation("Cursor does not match any known position");
        }
        return matching.subList(index + 1, matching.size());
    }

    private String positionKey(Transaction transaction) {
        return transaction.getOccurredOn() + "|" + transaction.getId().getValue();
    }
}
