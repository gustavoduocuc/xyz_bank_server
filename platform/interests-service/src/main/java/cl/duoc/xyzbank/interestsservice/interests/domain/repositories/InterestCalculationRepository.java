package cl.duoc.xyzbank.interestsservice.interests.domain.repositories;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;

import java.util.Optional;

public interface InterestCalculationRepository {

    void save(InterestCalculation calculation);

    Optional<InterestCalculation> findByEventId(String eventId);
}
