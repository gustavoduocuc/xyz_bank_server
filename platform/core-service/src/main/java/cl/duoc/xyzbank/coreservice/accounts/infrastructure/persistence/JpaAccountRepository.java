package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
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
        jpaRepository.save(toEntity(account));
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
                account.getBalance().getCurrency());
    }

    private Account toDomain(AccountJpaEntity entity) {
        return Account.create(
                Id.create(entity.getId().toString()),
                AccountNumber.create(entity.getAccountNumber()),
                Id.create(entity.getCustomerId().toString()),
                Money.create(entity.getBalance(), entity.getCurrency()));
    }
}
