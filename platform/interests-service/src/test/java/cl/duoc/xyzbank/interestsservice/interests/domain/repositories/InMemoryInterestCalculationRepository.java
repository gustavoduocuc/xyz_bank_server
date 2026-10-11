package cl.duoc.xyzbank.interestsservice.interests.domain.repositories;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInterestCalculationRepository implements InterestCalculationRepository {

    private final Map<String, InterestCalculation> calculations = new ConcurrentHashMap<>();

    @Override
    public void save(InterestCalculation calculation) {
        calculations.put(calculation.eventId(), calculation);
    }

    @Override
    public Optional<InterestCalculation> findByEventId(String eventId) {
        return Optional.ofNullable(calculations.get(eventId));
    }
}
