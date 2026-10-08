package cl.duoc.xyzbank.coreservice.postings.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.events.application.dto.ConfirmedMovementType;
import cl.duoc.xyzbank.coreservice.events.application.dto.TransactionConfirmed;
import cl.duoc.xyzbank.coreservice.events.application.ports.TransactionConfirmedPublisher;
import cl.duoc.xyzbank.coreservice.postings.application.ports.PostingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaPostingRepository implements PostingRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionConfirmedPublisher transactionConfirmedPublisher;

    public JpaPostingRepository(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionConfirmedPublisher transactionConfirmedPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionConfirmedPublisher = transactionConfirmedPublisher;
    }

    @Override
    @Transactional
    public void persistPosting(List<Account> accounts, List<Transaction> transactions) {
        accounts.forEach(accountRepository::save);
        for (Transaction transaction : transactions) {
            transactionRepository.save(transaction);
            transactionConfirmedPublisher.publish(new TransactionConfirmed(
                    transaction.getId().getValue(),
                    transaction.getAccountId().getValue(),
                    transaction.getType() == TransactionType.DEBIT
                            ? ConfirmedMovementType.PAYMENT_DEBIT
                            : ConfirmedMovementType.PAYMENT_CREDIT,
                    transaction.getAmount().getAmount(),
                    transaction.getAmount().getCurrency(),
                    transaction.getOccurredOn()));
        }
    }
}
