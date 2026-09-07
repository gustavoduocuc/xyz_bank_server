package cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataInterestSummaryRepository extends JpaRepository<AnnualInterestSummaryJpaEntity, UUID> {
    Optional<AnnualInterestSummaryJpaEntity> findByAccountIdAndYear(UUID accountId, int year);
}
