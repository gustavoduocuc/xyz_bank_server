package cl.duoc.xyzbank.coreservice.postings.application.ports;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;

import java.util.List;

public interface PostingRepository {

    /** Saves the accounts and the entries' transactions all together or not at all. */
    void persistPosting(List<Account> accounts, List<Transaction> transactions);
}
