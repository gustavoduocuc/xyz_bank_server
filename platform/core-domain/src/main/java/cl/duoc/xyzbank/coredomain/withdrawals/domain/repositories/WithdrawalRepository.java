package cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;

public interface WithdrawalRepository {
    void persistWithdrawal(Account account, Transaction transaction);
}
