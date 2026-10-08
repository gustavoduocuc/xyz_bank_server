package cl.duoc.xyzbank.coreservice.postings.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.postings.application.ports.PostingRepository;

import java.util.List;

public class InMemoryPostingRepository implements PostingRepository {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public InMemoryPostingRepository(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void persistPosting(List<Account> accounts, List<Transaction> transactions) {
        accounts.forEach(accountRepository::save);
        transactions.forEach(transactionRepository::save);
    }
}
