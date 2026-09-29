package cl.duoc.xyzbank.coredomain.cards.domain.entities;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;

import java.time.Duration;
import java.time.Instant;

/**
 * The 120-second window core-service opens after a card's PIN is verified: while it is active,
 * ATM calls referencing it act for the card's customer. core-service trusts only sessions it
 * opened itself, never a customer id asserted by the caller.
 */
public final class AtmSession {

    private static final Duration LIFETIME = Duration.ofSeconds(120);

    private final Id id;
    private final Id customerId;
    private final Id cardId;
    private final Instant expiresAt;

    private AtmSession(Id id, Id customerId, Id cardId, Instant expiresAt) {
        this.id = id;
        this.customerId = customerId;
        this.cardId = cardId;
        this.expiresAt = expiresAt;
    }

    public static AtmSession create(Id id, Id customerId, Id cardId, Instant expiresAt) {
        if (id == null || customerId == null || cardId == null || expiresAt == null) {
            throw DomainException.validation("An ATM session needs an id, a customer, a card and an expiry");
        }
        return new AtmSession(id, customerId, cardId, expiresAt);
    }

    public static AtmSession open(Id customerId, Id cardId, Instant now) {
        return create(Id.generate(), customerId, cardId, now.plus(LIFETIME));
    }

    public boolean isActiveAt(Instant instant) {
        return instant.isBefore(expiresAt);
    }

    public Id getId() {
        return id;
    }

    public Id getCustomerId() {
        return customerId;
    }

    public Id getCardId() {
        return cardId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
