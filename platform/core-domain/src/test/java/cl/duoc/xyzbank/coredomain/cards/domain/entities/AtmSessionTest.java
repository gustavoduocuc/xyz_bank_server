package cl.duoc.xyzbank.coredomain.cards.domain.entities;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The AtmSession")
class AtmSessionTest {

    /*
     * Cases:
     * 1. Opening a session for a verified card gives it a fresh id and a 120-second life
     * 2. A session is active before its expiry and inactive from its expiry on
     * 3. Two sessions opened at the same moment get different ids
     * 4. Rejects a session without a customer or a card
     */

    private static final Id CUSTOMER = Id.create("11111111-1111-1111-1111-111111111111");
    private static final Id CARD = Id.create("77777777-7777-7777-7777-777777777777");
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Test
    @DisplayName("opens with a fresh id and a 120-second life")
    void opensWithAFreshIdAndA120SecondLife() {
        AtmSession session = AtmSession.open(CUSTOMER, CARD, NOW);

        assertEquals(CUSTOMER, session.getCustomerId());
        assertEquals(CARD, session.getCardId());
        assertEquals(NOW.plus(Duration.ofSeconds(120)), session.getExpiresAt());
    }

    @Test
    @DisplayName("is active before its expiry and inactive from its expiry on")
    void isActiveBeforeItsExpiryAndInactiveFromItsExpiryOn() {
        AtmSession session = AtmSession.open(CUSTOMER, CARD, NOW);

        assertTrue(session.isActiveAt(NOW.plusSeconds(119)));
        assertFalse(session.isActiveAt(NOW.plusSeconds(120)));
    }

    @Test
    @DisplayName("gives two sessions opened at the same moment different ids")
    void givesTwoSessionsOpenedAtTheSameMomentDifferentIds() {
        assertNotEquals(AtmSession.open(CUSTOMER, CARD, NOW).getId(), AtmSession.open(CUSTOMER, CARD, NOW).getId());
    }

    @Test
    @DisplayName("rejects a session without a customer or a card")
    void rejectsASessionWithoutACustomerOrACard() {
        assertThrows(DomainException.class, () -> AtmSession.open(null, CARD, NOW));
        assertThrows(DomainException.class, () -> AtmSession.open(CUSTOMER, null, NOW));
    }
}
