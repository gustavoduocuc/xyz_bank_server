package cl.duoc.xyzbank.coredomain.transactions.domain.repositories;

import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionPage;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;

import java.util.Optional;

public interface TransactionRepository {
    void save(Transaction transaction);

    Optional<Transaction> findById(Id id);

    TransactionPage findByAccountId(
            Id accountId,
            DateRange dateRange,
            Optional<TransactionType> type,
            Optional<Cursor> cursor,
            int pageSize);
}
