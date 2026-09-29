package cl.duoc.xyzbank.coredomain.cards.domain.repositories;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.util.Optional;

public interface AtmSessionRepository {

    void save(AtmSession session);

    Optional<AtmSession> findById(Id id);
}
