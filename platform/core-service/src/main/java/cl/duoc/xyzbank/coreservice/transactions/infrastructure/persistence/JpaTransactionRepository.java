package cl.duoc.xyzbank.coreservice.transactions.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionPage;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final SpringDataTransactionRepository jpaRepository;

    public JpaTransactionRepository(SpringDataTransactionRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Transaction transaction) {
        jpaRepository.save(toEntity(transaction));
    }

    @Override
    public Optional<Transaction> findById(Id id) {
        return jpaRepository.findById(UUID.fromString(id.getValue()))
                .map(this::toDomain);
    }

    @Override
    public TransactionPage findByAccountId(
            Id accountId, DateRange dateRange, Optional<TransactionType> type, Optional<Cursor> cursor, int pageSize) {
        LocalDate cursorOccurredOn = null;
        UUID cursorId = null;
        if (cursor.isPresent()) {
            CursorPosition position = decodeCursor(cursor.get());
            cursorOccurredOn = position.occurredOn();
            cursorId = position.id();
        }

        List<TransactionJpaEntity> rows = jpaRepository.findPage(
                UUID.fromString(accountId.getValue()),
                dateRange.getFrom().orElse(null),
                dateRange.getTo().orElse(null),
                type.orElse(null),
                cursorOccurredOn,
                cursorId,
                PageRequest.of(0, pageSize + 1));

        List<Transaction> items = rows.stream().limit(pageSize).map(this::toDomain).toList();
        Optional<Cursor> nextCursor = rows.size() > pageSize
                ? Optional.of(encodeCursor(rows.get(pageSize - 1)))
                : Optional.empty();

        return TransactionPage.create(items, nextCursor);
    }

    private Cursor encodeCursor(TransactionJpaEntity entity) {
        String raw = entity.getOccurredOn() + "|" + entity.getId();
        return Cursor.create(Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private CursorPosition decodeCursor(Cursor cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor.getValue()), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new CursorPosition(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw DomainException.validation("Cursor is malformed");
        }
    }

    private TransactionJpaEntity toEntity(Transaction transaction) {
        return new TransactionJpaEntity(
                UUID.fromString(transaction.getId().getValue()),
                UUID.fromString(transaction.getAccountId().getValue()),
                transaction.getType(),
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn(),
                transaction.getDescription());
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return Transaction.create(
                Id.create(entity.getId().toString()),
                Id.create(entity.getAccountId().toString()),
                entity.getType(),
                Money.create(entity.getAmount(), entity.getCurrency()),
                entity.getOccurredOn(),
                entity.getDescription());
    }

    private record CursorPosition(LocalDate occurredOn, UUID id) {
    }
}
