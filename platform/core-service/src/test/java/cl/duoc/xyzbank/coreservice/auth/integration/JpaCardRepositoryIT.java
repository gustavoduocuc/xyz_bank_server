package cl.duoc.xyzbank.coreservice.auth.integration;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.persistence.JpaCardRepository;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA card repository")
class JpaCardRepositoryIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Saves a card and finds it by card number
     * 2. Returns empty when no card matches the card number
     * 3. Returns empty for a malformed (non-UUID) card number, rather than throwing
     * 4. Rejects a save based on a stale version (optimistic lock conflict)
     */

    private final cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher bcryptHasher =
            new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher();
    private final PinHasher hasher = bcryptHasher::matches;

    @Autowired
    private JpaCardRepository cardRepository;

    private Id newCustomer() {
        Id customerId = Id.generate();
        return customerId;
    }

    @Test
    @DisplayName("saves a card and finds it by card number")
    void savesACardAndFindsItByCardNumber() {
        Id id = Id.generate();
        Card card = Card.create(id, newCustomer(), bcryptHasher.hash("1234"), 0, false, 0L);

        cardRepository.save(card);
        Optional<Card> found = cardRepository.findByCardNumber(id);

        assertTrue(found.isPresent());
        assertEquals(Card.PinVerificationResult.SUCCESS, found.get().verifyPin("1234", hasher));
    }

    @Test
    @DisplayName("returns empty when no card matches the card number")
    void returnsEmptyWhenNoCardMatches() {
        Optional<Card> found = cardRepository.findByCardNumber(Id.generate());

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("returns empty for a malformed (non-uuid) card number, rather than throwing")
    void returnsEmptyForMalformedCardNumber() {
        Optional<Card> found = cardRepository.findByCardNumber(Id.create("not-a-uuid"));

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("rejects a save based on a stale version")
    void rejectsASaveBasedOnAStaleVersion() {
        Id id = Id.generate();
        Card original = Card.create(id, newCustomer(), bcryptHasher.hash("1234"), 0, false, 0L);
        cardRepository.save(original);
        Card firstCopy = cardRepository.findByCardNumber(id).orElseThrow();
        Card secondCopy = cardRepository.findByCardNumber(id).orElseThrow();

        firstCopy.verifyPin("9999", hasher);
        cardRepository.save(firstCopy);

        secondCopy.verifyPin("9999", hasher);
        DomainException exception = assertThrows(DomainException.class, () -> cardRepository.save(secondCopy));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }
}
