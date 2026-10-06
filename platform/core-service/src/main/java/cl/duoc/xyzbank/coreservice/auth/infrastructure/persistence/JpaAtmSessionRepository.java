package cl.duoc.xyzbank.coreservice.auth.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAtmSessionRepository implements AtmSessionRepository {

    private final SpringDataAtmSessionRepository jpaRepository;

    public JpaAtmSessionRepository(SpringDataAtmSessionRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(AtmSession session) {
        jpaRepository.saveAndFlush(new AtmSessionJpaEntity(
                UUID.fromString(session.getId().getValue()),
                UUID.fromString(session.getCustomerId().getValue()),
                UUID.fromString(session.getCardId().getValue()),
                session.getExpiresAt()));
    }

    @Override
    public Optional<AtmSession> findById(Id id) {
        // A malformed id can never match a stored session; answering "not found" keeps
        // garbage input indistinguishable from an unknown session
        UUID uuid;
        try {
            uuid = UUID.fromString(id.getValue());
        } catch (IllegalArgumentException malformedId) {
            return Optional.empty();
        }
        return jpaRepository.findById(uuid).map(entity -> AtmSession.create(
                Id.create(entity.getId().toString()),
                Id.create(entity.getCustomerId().toString()),
                Id.create(entity.getCardId().toString()),
                entity.getExpiresAt()));
    }
}
