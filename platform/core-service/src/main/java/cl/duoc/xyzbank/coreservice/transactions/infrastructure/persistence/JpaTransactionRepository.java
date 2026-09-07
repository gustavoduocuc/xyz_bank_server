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
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public TransactionPage findByAccountId(
            Id accountId, DateRange dateRange, Optional<TransactionType> type, Optional<Cursor> cursor, int pageSize) {
        Optional<CursorPosition> position = cursor.map(this::decodeCursor);

        Specification<TransactionJpaEntity> spec = buildSpecification(accountId, dateRange, type, position);
        Sort sort = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("id"));

        List<TransactionJpaEntity> rows = jpaRepository
                .findAll(spec, PageRequest.of(0, pageSize + 1, sort))
                .getContent();

        List<Transaction> items = rows.stream().limit(pageSize).map(this::toDomain).toList();
        Optional<Cursor> nextCursor = rows.size() > pageSize
                ? Optional.of(encodeCursor(rows.get(pageSize - 1)))
                : Optional.empty();

        return TransactionPage.create(items, nextCursor);
    }

    private Specification<TransactionJpaEntity> buildSpecification(
            Id accountId, DateRange dateRange, Optional<TransactionType> type, Optional<CursorPosition> cursor) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("accountId"), UUID.fromString(accountId.getValue())));
            dateRange.getFrom().ifPresent(from -> predicates.add(builder.greaterThanOrEqualTo(root.get("occurredOn"), from)));
            dateRange.getTo().ifPresent(to -> predicates.add(builder.lessThanOrEqualTo(root.get("occurredOn"), to)));
            type.ifPresent(t -> predicates.add(builder.equal(root.get("type"), t)));
            cursor.ifPresent(position -> predicates.add(builder.or(
                    builder.lessThan(root.get("occurredOn"), position.occurredOn()),
                    builder.and(
                            builder.equal(root.get("occurredOn"), position.occurredOn()),
                            builder.lessThan(root.get("id"), position.id())))));
            return builder.and(predicates.toArray(new Predicate[0]));
        };
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
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException exception) {
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
                transaction.getDescription(),
                transaction.getIdempotencyKey().orElse(null));
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return Transaction.create(
                Id.create(entity.getId().toString()),
                Id.create(entity.getAccountId().toString()),
                entity.getType(),
                Money.create(entity.getAmount(), entity.getCurrency()),
                entity.getOccurredOn(),
                entity.getDescription(),
                Optional.ofNullable(entity.getIdempotencyKey()));
    }

    private record CursorPosition(LocalDate occurredOn, UUID id) {
    }
}
