package cl.duoc.xyzbank.coredomain.interests.domain.repositories;

import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Optional;

public interface InterestSummaryRepository {
    void save(AnnualInterestSummary summary);

    Optional<AnnualInterestSummary> findByAccountIdAndYear(Id accountId, int year);
}
