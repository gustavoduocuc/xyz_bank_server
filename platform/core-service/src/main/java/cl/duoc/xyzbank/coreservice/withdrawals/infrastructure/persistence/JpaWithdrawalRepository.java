package cl.duoc.xyzbank.coreservice.withdrawals.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories.WithdrawalRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaWithdrawalRepository implements WithdrawalRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public JpaWithdrawalRepository(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public void persistWithdrawal(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }
}
