package cl.duoc.xyzbank.coredomain.cards.unit;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAtmSessionRepository implements AtmSessionRepository {

    private final Map<String, AtmSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AtmSession session) {
        sessions.put(session.getId().getValue(), session);
    }

    @Override
    public Optional<AtmSession> findById(Id id) {
        return Optional.ofNullable(sessions.get(id.getValue()));
    }

    public List<AtmSession> all() {
        return List.copyOf(sessions.values());
    }
}
