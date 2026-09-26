package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class JpaInterestCreditRepository implements InterestCreditRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InterestSummaryRepository interestSummaryRepository;
    private final JdbcTemplate jdbcTemplate;

    public JpaInterestCreditRepository(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.interestSummaryRepository = interestSummaryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void persistInterestCredit(Account account, Transaction transaction, AnnualInterestSummary summary) {
        try {
            accountRepository.save(account);
            transactionRepository.save(transaction);
            interestSummaryRepository.save(summary);
            recordInterestCredit(account, transaction, summary);
        } catch (DataIntegrityViolationException exception) {
            throw DomainException.conflict("Interest already credited for this account and year");
        }
    }

    private void recordInterestCredit(Account account, Transaction transaction, AnnualInterestSummary summary) {
        String eventId = transaction.getIdempotencyKey()
                .orElseThrow(() -> DomainException.validation("Idempotency key is required"));
        jdbcTemplate.update(
                "INSERT INTO processed_interest_events (event_id, idempotency_key) VALUES (?, ?)",
                eventId,
                eventId);
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, event_id, event_type, schema_version, account_id, period,
                    amount, currency, interest_rate, opening_balance, closing_balance, occurred_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                eventId,
                "InterestCreditApplied",
                1,
                UUID.fromString(account.getId().getValue()),
                summary.getYear(),
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                summary.getInterestRate(),
                summary.getOpeningBalance().getAmount(),
                summary.getClosingBalance().getAmount(),
                transaction.getOccurredOn());
    }
}
