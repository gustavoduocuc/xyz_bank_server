package cl.duoc.xyzbank.coreservice.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "atm_sessions")
public class AtmSessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AtmSessionJpaEntity() {
    }

    public AtmSessionJpaEntity(UUID id, UUID customerId, UUID cardId, Instant expiresAt) {
        this.id = id;
        this.customerId = customerId;
        this.cardId = cardId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
