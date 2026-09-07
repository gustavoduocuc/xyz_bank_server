package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
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
            jpaRepository.save(toEntity(account));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw DomainException.conflict("Account was updated concurrently");
        }
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

    private AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
                UUID.fromString(account.getId().getValue()),
                account.getAccountNumber().getValue(),
                UUID.fromString(account.getCustomerId().getValue()),
                account.getBalance().getAmount(),
                account.getBalance().getCurrency(),
                account.getVersion(),
                account.getDailyWithdrawnAmount().getAmount(),
                account.getDailyWithdrawnDate().orElse(null));
    }

    private Account toDomain(AccountJpaEntity entity) {
        return Account.create(
                Id.create(entity.getId().toString()),
                AccountNumber.create(entity.getAccountNumber()),
                Id.create(entity.getCustomerId().toString()),
                Money.create(entity.getBalance(), entity.getCurrency()),
                entity.getVersion(),
                Money.create(entity.getDailyWithdrawnAmount(), entity.getCurrency()),
                Optional.ofNullable(entity.getDailyWithdrawnDate()));
    }
}
