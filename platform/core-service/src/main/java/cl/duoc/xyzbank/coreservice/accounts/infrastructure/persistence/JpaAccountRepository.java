package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.DailyWithdrawalUsage;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAccountRepository implements AccountRepository {

    private final SpringDataAccountRepository jpaRepository;

    public JpaAccountRepository(SpringDataAccountRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Account account) {
        try {
            // saveAndFlush (not save): merge()-based updates defer the actual UPDATE, and
            // therefore the version check, to flush time. Without an explicit flush here,
            // a stale-version failure would surface later at @Transactional commit, outside
            // this try/catch, as an uncaught exception instead of a clean domain conflict.
            jpaRepository.saveAndFlush(toEntity(account, null));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw DomainException.conflict("Account was updated concurrently");
        }
    }

    /** Runs in its own transaction, so a losing duplicate insert can re-read the winning row. */
    @Override
    public Account saveOpened(Account account, String openingIdempotencyKey) {
        try {
            return toDomain(jpaRepository.saveAndFlush(toEntity(account, openingIdempotencyKey)));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return findByOpeningIdempotencyKey(openingIdempotencyKey).orElseThrow(() -> concurrentDuplicate);
        }
    }

    @Override
    public Optional<Account> findByOpeningIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByOpeningIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<Account> findById(Id id) {
        return jpaRepository.findById(UUID.fromString(id.getValue()))
                .map(this::toDomain);
    }

    @Override
    public List<Account> findByCustomerId(Id customerId) {
        return jpaRepository.findByCustomerId(UUID.fromString(customerId.getValue())).stream()
                .map(this::toDomain)
                .toList();
    }

    /** The opening key column is not updatable, so passing null on later saves keeps the stored key. */
    private AccountJpaEntity toEntity(Account account, String openingIdempotencyKey) {
        return new AccountJpaEntity(
                UUID.fromString(account.getId().getValue()),
                account.getAccountNumber().getValue(),
                UUID.fromString(account.getCustomerId().getValue()),
                account.getBalance().getAmount(),
                account.getBalance().getCurrency(),
                account.getVersion(),
                account.getDailyWithdrawnAmount().getAmount(),
                account.getDailyWithdrawnDate().orElse(null),
                account.getStatus().name(),
                account.getAlias().orElse(null),
                account.getDailyWithdrawalLimit().map(Money::getAmount).orElse(null),
                openingIdempotencyKey,
                account.getLastCommandIdempotencyKey().orElse(null));
    }

    private Account toDomain(AccountJpaEntity entity) {
        return Account.create(
                Id.create(entity.getId().toString()),
                AccountNumber.create(entity.getAccountNumber()),
                Id.create(entity.getCustomerId().toString()),
                Money.create(entity.getBalance(), entity.getCurrency()),
                entity.getVersion(),
                DailyWithdrawalUsage.create(
                        Money.create(entity.getDailyWithdrawnAmount(), entity.getCurrency()),
                        Optional.ofNullable(entity.getDailyWithdrawnDate())),
                new Account.AccountLifecycle(
                        AccountStatus.valueOf(entity.getStatus()),
                        entity.getAlias(),
                        entity.getDailyWithdrawalLimit() == null
                                ? null
                                : Money.create(entity.getDailyWithdrawalLimit(), entity.getCurrency()),
                        entity.getLastCommandIdempotencyKey()));
    }
}
