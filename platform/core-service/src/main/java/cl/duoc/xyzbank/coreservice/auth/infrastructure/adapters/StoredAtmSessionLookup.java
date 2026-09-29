package cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;

import java.time.Clock;
import java.util.Optional;

/**
 * Resolves the customer of an ATM session core-service opened, only while it is active.
 */
public class StoredAtmSessionLookup implements AtmSessionLookup {

    private final AtmSessionRepository atmSessions;
    private final Clock clock;

    public StoredAtmSessionLookup(AtmSessionRepository atmSessions, Clock clock) {
        this.atmSessions = atmSessions;
        this.clock = clock;
    }

    @Override
    public Optional<String> activeCustomerOf(String atmSessionId) {
        Id id;
        try {
            id = Id.create(atmSessionId);
        } catch (DomainException blankId) {
            return Optional.empty();
        }
        return atmSessions.findById(id)
                .filter(session -> session.isActiveAt(clock.instant()))
                .map(AtmSession::getCustomerId)
                .map(Id::getValue);
    }
}
