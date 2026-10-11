package cl.duoc.xyzbank.coredomain.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, String> accountIdsByOpeningKey = new ConcurrentHashMap<>();

    public InMemoryAccountRepository() {
    }

    public InMemoryAccountRepository(List<Account> initialAccounts) {
        initialAccounts.forEach(this::save);
    }

    @Override
    public void save(Account account) {
        accounts.put(account.getId().getValue(), account);
    }

    @Override
    public Optional<Account> findById(Id id) {
        return Optional.ofNullable(accounts.get(id.getValue()));
    }

    @Override
    public List<Account> findByCustomerId(Id customerId) {
        return accounts.values().stream()
                .filter(account -> account.getCustomerId().equals(customerId))
                .toList();
    }

    @Override
    public Optional<Account> findByOpeningIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(accountIdsByOpeningKey.get(idempotencyKey)).map(accounts::get);
    }

    @Override
    public Account saveOpened(Account account, String openingIdempotencyKey) {
        String existing = accountIdsByOpeningKey.putIfAbsent(openingIdempotencyKey, account.getId().getValue());
        if (existing != null) {
            return accounts.get(existing);
        }
        save(account);
        return account;
    }

    public int size() {
        return accounts.size();
    }
}
