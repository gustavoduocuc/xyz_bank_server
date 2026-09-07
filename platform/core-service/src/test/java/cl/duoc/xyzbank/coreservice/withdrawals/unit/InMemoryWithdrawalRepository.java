package cl.duoc.xyzbank.coreservice.withdrawals.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories.WithdrawalRepository;

public class InMemoryWithdrawalRepository implements WithdrawalRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public InMemoryWithdrawalRepository(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void persistWithdrawal(Account account, Transaction transaction) {
        accountRepository.save(account);
        transactionRepository.save(transaction);
    }
}
