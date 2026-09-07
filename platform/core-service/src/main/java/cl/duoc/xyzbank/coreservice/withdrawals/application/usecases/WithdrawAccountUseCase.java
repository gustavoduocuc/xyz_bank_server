package cl.duoc.xyzbank.coreservice.withdrawals.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories.WithdrawalRepository;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawRequest;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawalResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public class WithdrawAccountUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final BigDecimal dailyLimitAmount;
    private final Clock clock;

    public WithdrawAccountUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            WithdrawalRepository withdrawalRepository,
            BigDecimal dailyLimitAmount,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.dailyLimitAmount = dailyLimitAmount;
        this.clock = clock;
    }

    public WithdrawalResponse execute(WithdrawRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw DomainException.validation("Idempotency key is required");
        }

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        if (request.amount() == null || request.amount().signum() <= 0) {
            throw DomainException.validation("Amount must be positive");
        }

        Id accountId = Id.create(request.accountId());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> DomainException.notFound("Account " + request.accountId() + " not found"));

        Money amount = Money.create(request.amount(), request.currency());
        LocalDate today = LocalDate.now(clock);
        Money dailyLimit = Money.create(dailyLimitAmount, account.getBalance().getCurrency());

        account.withdraw(amount, today, dailyLimit);

        Transaction transaction = Transaction.create(
                Id.generate(), accountId, TransactionType.DEBIT, amount, today, null,
                Optional.of(request.idempotencyKey()));

        withdrawalRepository.persistWithdrawal(account, transaction);

        return toResponse(transaction, account.getBalance());
    }

    private WithdrawalResponse replay(Transaction existing, WithdrawRequest request) {
        boolean sameAccount = existing.getAccountId().equals(Id.create(request.accountId()));
        boolean sameAmount = existing.getAmount().getAmount().compareTo(request.amount()) == 0
                && existing.getAmount().getCurrency().equals(request.currency());
        if (!sameAccount || !sameAmount) {
            throw DomainException.conflict("Idempotency key already used with different parameters");
        }

        Account account = accountRepository.findById(existing.getAccountId())
                .orElseThrow(() -> DomainException.notFound("Account " + request.accountId() + " not found"));
        return toResponse(existing, account.getBalance());
    }

    private WithdrawalResponse toResponse(Transaction transaction, Money newBalance) {
        return new WithdrawalResponse(
                transaction.getId().getValue(),
                transaction.getAccountId().getValue(),
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn().toString(),
                newBalance.getAmount());
    }
}
