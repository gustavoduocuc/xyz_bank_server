package cl.duoc.xyzbank.coreservice.withdrawals.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories.WithdrawalRepository;
import cl.duoc.xyzbank.coreservice.events.application.dto.ConfirmedMovementType;
import cl.duoc.xyzbank.coreservice.events.application.dto.TransactionConfirmed;
import cl.duoc.xyzbank.coreservice.events.application.ports.TransactionConfirmedPublisher;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaWithdrawalRepository implements WithdrawalRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionConfirmedPublisher transactionConfirmedPublisher;

    public JpaWithdrawalRepository(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionConfirmedPublisher transactionConfirmedPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionConfirmedPublisher = transactionConfirmedPublisher;
    }

    @Override
    @Transactional
    public void persistWithdrawal(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
        transactionConfirmedPublisher.publish(new TransactionConfirmed(
                transaction.getId().getValue(),
                account.getId().getValue(),
                account.getCustomerId().getValue(),
                ConfirmedMovementType.WITHDRAWAL,
                transaction.getAmount().getAmount(),
                transaction.getAmount().getCurrency(),
                transaction.getOccurredOn()));
    }
}
