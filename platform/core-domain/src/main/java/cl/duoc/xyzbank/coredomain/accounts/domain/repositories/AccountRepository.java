package cl.duoc.xyzbank.coredomain.accounts.domain.repositories;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    void save(Account account);

    Optional<Account> findById(Id id);

    List<Account> findByCustomerId(Id customerId);

    Optional<Account> findByOpeningIdempotencyKey(String idempotencyKey);

    /** Saves a newly opened account under its opening key; if the key is taken, returns that key's account. */
    Account saveOpened(Account account, String openingIdempotencyKey);
}
