package cl.duoc.xyzbank.coredomain.interests.unit;

import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInterestSummaryRepository implements InterestSummaryRepository {

    private final Map<String, AnnualInterestSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public void save(AnnualInterestSummary summary) {
        summaries.put(key(summary.getAccountId(), summary.getYear()), summary);
    }

    @Override
    public Optional<AnnualInterestSummary> findByAccountIdAndYear(Id accountId, int year) {
        return Optional.ofNullable(summaries.get(key(accountId, year)));
    }

    private String key(Id accountId, int year) {
        return accountId.getValue() + "|" + year;
    }
}
