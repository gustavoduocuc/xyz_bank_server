package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coredomain.interests.domain.repositories.ProcessedInterestEventRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryProcessedInterestEventRepository implements ProcessedInterestEventRepository {

    private final Map<String, String> idempotencyKeyByEventId = new HashMap<>();

    @Override
    public Optional<String> findIdempotencyKey(String eventId) {
        return Optional.ofNullable(idempotencyKeyByEventId.get(eventId));
    }

    @Override
    public void register(String eventId, String idempotencyKey) {
        idempotencyKeyByEventId.put(eventId, idempotencyKey);
    }
}
