package cl.duoc.xyzbank.coredomain.interests.domain.repositories;

import java.util.Optional;

public interface ProcessedInterestEventRepository {

    Optional<String> findIdempotencyKey(String eventId);

    void register(String eventId, String idempotencyKey);
}
