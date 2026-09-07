package cl.duoc.xyzbank.coreservice.transactions.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.Cursor;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionPage;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.ListAccountTransactionsRequest;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionPageResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionSummaryResponse;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class ListAccountTransactionsUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public ListAccountTransactionsUseCase(
            AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public TransactionPageResponse execute(ListAccountTransactionsRequest request) {
        Id accountId = Id.create(request.accountId());
        accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + request.accountId() + " not found"));

        DateRange dateRange = DateRange.create(parseDate(request.from()), parseDate(request.to()));
        Optional<TransactionType> type = parseType(request.type());
        Optional<Cursor> cursor = Optional.ofNullable(request.cursor()).map(Cursor::create);
        int pageSize = resolvePageSize(request.pageSize());

        TransactionPage page = transactionRepository.findByAccountId(accountId, dateRange, type, cursor, pageSize);

        return toResponse(page);
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException exception) {
            throw DomainException.validation("Date must be in yyyy-MM-dd format");
        }
    }

    private Optional<TransactionType> parseType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TransactionType.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw DomainException.validation("Type must be DEBIT or CREDIT");
        }
    }

    private int resolvePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requested <= 0) {
            throw DomainException.validation("Page size must be positive");
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private TransactionPageResponse toResponse(TransactionPage page) {
        return new TransactionPageResponse(
                page.getItems().stream().map(this::toSummary).toList(),
                page.getNextCursor().map(Cursor::getValue).orElse(null));
    }

    private TransactionSummaryResponse toSummary(Transaction transaction) {
        return new TransactionSummaryResponse(
                transaction.getId().getValue(),
                transaction.getType().name(),
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                transaction.getDescription());
    }
}
