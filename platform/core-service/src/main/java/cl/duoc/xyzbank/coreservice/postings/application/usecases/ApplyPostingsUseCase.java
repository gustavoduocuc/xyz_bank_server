package cl.duoc.xyzbank.coreservice.postings.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingEntry;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingRequest;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingResponse;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingResponse.PostedEntry;
import cl.duoc.xyzbank.coreservice.postings.application.ports.PostingRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies a payment's debit and/or credit entries at most once: each entry is a transaction keyed
 * {@code payment:{paymentId}:{direction}}, so a repeated paymentId replays the recorded transactions.
 */
public class ApplyPostingsUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PostingRepository postingRepository;
    private final Clock clock;

    public ApplyPostingsUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PostingRepository postingRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.postingRepository = postingRepository;
        this.clock = clock;
    }

    public PostingResponse execute(PostingRequest request) {
        validate(request);

        List<Transaction> recorded = request.entries().stream()
                .map(entry -> transactionRepository.findByIdempotencyKey(keyOf(request.paymentId(), entry)))
                .flatMap(Optional::stream)
                .toList();
        if (!recorded.isEmpty()) {
            return toResponse(request.paymentId(), recorded, transaction -> findAccountOrThrow(transaction.getAccountId()));
        }

        LocalDate today = LocalDate.now(clock);
        List<Account> accounts = new ArrayList<>();
        List<Transaction> transactions = new ArrayList<>();
        for (PostingEntry entry : request.entries()) {
            Account account = findAccountOrThrow(Id.create(entry.accountId()));
            Money amount = Money.create(entry.amount(), entry.currency());
            TransactionType type = TransactionType.valueOf(entry.direction());
            if (type == TransactionType.DEBIT) {
                account.debit(amount);
            } else {
                account.credit(amount);
            }
            accounts.add(account);
            transactions.add(Transaction.create(Id.generate(), account.getId(), type, amount, today,
                    "Payment " + request.paymentId(), Optional.of(keyOf(request.paymentId(), entry))));
        }

        postingRepository.persistPosting(accounts, transactions);

        Map<Id, Account> accountsById = accounts.stream().collect(Collectors.toMap(Account::getId, account -> account));
        return toResponse(request.paymentId(), transactions, transaction -> accountsById.get(transaction.getAccountId()));
    }

    private void validate(PostingRequest request) {
        requireUuid(request.paymentId());
        List<PostingEntry> entries = request.entries() == null ? List.of() : request.entries();
        if (entries.isEmpty() || entries.size() > 2) {
            throw DomainException.validation("A posting has one or two entries");
        }
        for (PostingEntry entry : entries) {
            if (entry.accountId() == null || entry.accountId().isBlank()) {
                throw DomainException.validation("Each entry needs an accountId");
            }
            if (!"DEBIT".equals(entry.direction()) && !"CREDIT".equals(entry.direction())) {
                throw DomainException.validation("An entry's direction is DEBIT or CREDIT");
            }
            if (entry.amount() == null || entry.amount().signum() <= 0) {
                throw DomainException.validation("Amount must be positive");
            }
        }
        if (entries.size() == 2) {
            PostingEntry first = entries.get(0);
            PostingEntry second = entries.get(1);
            if (first.direction().equals(second.direction()) || first.accountId().equals(second.accountId())) {
                throw DomainException.validation("Two entries must be one DEBIT and one CREDIT on different accounts");
            }
        }
    }

    private static void requireUuid(String paymentId) {
        try {
            UUID.fromString(paymentId == null ? "" : paymentId);
        } catch (IllegalArgumentException e) {
            throw DomainException.validation("paymentId must be a UUID");
        }
    }

    private static String keyOf(String paymentId, PostingEntry entry) {
        return "payment:" + paymentId + ":" + entry.direction();
    }

    private Account findAccountOrThrow(Id accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId.getValue() + " not found"));
    }

    private static PostingResponse toResponse(
            String paymentId, List<Transaction> transactions, Function<Transaction, Account> accountOf) {
        return new PostingResponse(paymentId, transactions.stream()
                .map(transaction -> new PostedEntry(
                        transaction.getId().getValue(),
                        transaction.getAccountId().getValue(),
                        transaction.getType().name(),
                        transaction.getAmount().getAmount(),
                        transaction.getAmount().getCurrency(),
                        accountOf.apply(transaction).getBalance().getAmount()))
                .toList());
    }
}
