package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaInterestSummaryRepository implements InterestSummaryRepository {

    private final SpringDataInterestSummaryRepository jpaRepository;

    public JpaInterestSummaryRepository(SpringDataInterestSummaryRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(AnnualInterestSummary summary) {
        jpaRepository.save(toEntity(summary));
    }

    @Override
    public Optional<AnnualInterestSummary> findByAccountIdAndYear(Id accountId, int year) {
        return jpaRepository.findByAccountIdAndYear(UUID.fromString(accountId.getValue()), year)
                .map(this::toDomain);
    }

    private AnnualInterestSummaryJpaEntity toEntity(AnnualInterestSummary summary) {
        return new AnnualInterestSummaryJpaEntity(
                UUID.fromString(summary.getId().getValue()),
                UUID.fromString(summary.getAccountId().getValue()),
                summary.getYear(),
                summary.getOpeningBalance().getAmount(),
                summary.getClosingBalance().getAmount(),
                summary.getInterestRate(),
                summary.getInterestAmount().getAmount(),
                summary.getOpeningBalance().getCurrency());
    }

    private AnnualInterestSummary toDomain(AnnualInterestSummaryJpaEntity entity) {
        return AnnualInterestSummary.create(
                Id.create(entity.getId().toString()),
                Id.create(entity.getAccountId().toString()),
                entity.getYear(),
                Money.create(entity.getOpeningBalance(), entity.getCurrency()),
                Money.create(entity.getClosingBalance(), entity.getCurrency()),
                entity.getInterestRate(),
                Money.create(entity.getInterestAmount(), entity.getCurrency()));
    }
}
