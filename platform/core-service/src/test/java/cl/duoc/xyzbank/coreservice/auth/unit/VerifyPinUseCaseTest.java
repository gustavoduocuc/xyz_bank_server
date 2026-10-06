package cl.duoc.xyzbank.coreservice.auth.unit;

import cl.duoc.xyzbank.coredomain.auth.unit.InMemoryCardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coredomain.cards.unit.InMemoryAtmSessionRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The VerifyPin use case")
class VerifyPinUseCaseTest {

    /*
     * Cases:
     * 1. A correct PIN succeeds, resets the card's failure count, and reports the owning customer id
     * 2. An incorrect PIN fails and increments the card's failure count
     * 3. An unknown card number fails identically to an incorrect PIN (no existence oracle)
     * 4. A third consecutive failure locks the card and is reported distinctly from a plain incorrect PIN
     * 5. A locked card rejects a subsequent correct PIN
     * 6. A correct PIN opens a 120-second ATM session for the card's customer and reports its id
     * 7. An incorrect PIN or a locked card opens no ATM session
     */

    private final cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher bcryptHasher =
            new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher();
    private final PinHasher hasher = bcryptHasher::matches;
    private final InMemoryCardRepository cardRepository = new InMemoryCardRepository();
    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private final InMemoryAtmSessionRepository atmSessionRepository = new InMemoryAtmSessionRepository();
    private final VerifyPinUseCase useCase = new VerifyPinUseCase(
            cardRepository, hasher, atmSessionRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a correct pin succeeds, resets the card's failure count, and reports the owning customer id")
    void correctPinSucceedsAndResetsFailureCount() {
        Id cardNumber = Id.generate();
        Id customerId = Id.generate();
        cardRepository.save(Card.create(cardNumber, customerId, bcryptHasher.hash("1234"), 2, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), "1234");

        assertEquals(PinVerificationOutcome.Result.SUCCESS, outcome.result());
        assertEquals(customerId.getValue(), outcome.customerId());
        assertEquals(0, cardRepository.findByCardNumber(cardNumber).orElseThrow().getConsecutiveFailures());
    }

    @Test
    @DisplayName("an incorrect pin fails and increments the card's failure count")
    void incorrectPinFailsAndIncrementsFailureCount() {
        Id cardNumber = Id.generate();
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash("1234"), 0, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), "9999");

        assertEquals(PinVerificationOutcome.Result.INCORRECT, outcome.result());
        assertNull(outcome.customerId());
        assertEquals(1, cardRepository.findByCardNumber(cardNumber).orElseThrow().getConsecutiveFailures());
    }

    @Test
    @DisplayName("an unknown card number fails identically to an incorrect pin")
    void unknownCardNumberFailsIdenticallyToIncorrectPin() {
        PinVerificationOutcome outcome = useCase.execute(Id.generate().getValue(), "1234");

        assertEquals(PinVerificationOutcome.Result.INCORRECT, outcome.result());
        assertNull(outcome.customerId());
    }

    @Test
    @DisplayName("a third consecutive failure locks the card")
    void thirdConsecutiveFailureLocksCard() {
        Id cardNumber = Id.generate();
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash("1234"), 2, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), "9999");

        assertEquals(PinVerificationOutcome.Result.LOCKED, outcome.result());
        assertNull(outcome.customerId());
        assertTrue(cardRepository.findByCardNumber(cardNumber).orElseThrow().isLocked());
    }

    @Test
    @DisplayName("a locked card rejects a subsequent correct pin")
    void lockedCardRejectsSubsequentCorrectPin() {
        Id cardNumber = Id.generate();
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash("1234"), 3, true, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), "1234");

        assertEquals(PinVerificationOutcome.Result.LOCKED, outcome.result());
        assertNull(outcome.customerId());
    }

    @Test
    @DisplayName("a correct pin opens a 120-second ATM session for the card's customer and reports its id")
    void correctPinOpensAnAtmSessionForTheCardsCustomer() {
        Id cardNumber = Id.generate();
        Id customerId = Id.generate();
        cardRepository.save(Card.create(cardNumber, customerId, bcryptHasher.hash("1234"), 0, false, 0L));

        PinVerificationOutcome outcome = useCase.execute(cardNumber.getValue(), "1234");

        AtmSession session = atmSessionRepository.findById(Id.create(outcome.atmSessionId())).orElseThrow();
        assertEquals(customerId, session.getCustomerId());
        assertEquals(cardNumber, session.getCardId());
        assertEquals(NOW.plusSeconds(120), session.getExpiresAt());
    }

    @Test
    @DisplayName("an incorrect pin or a locked card opens no ATM session")
    void incorrectPinOrLockedCardOpensNoAtmSession() {
        Id cardNumber = Id.generate();
        Id lockedCardNumber = Id.generate();
        cardRepository.save(Card.create(cardNumber, Id.generate(), bcryptHasher.hash("1234"), 0, false, 0L));
        cardRepository.save(Card.create(lockedCardNumber, Id.generate(), bcryptHasher.hash("1234"), 3, true, 0L));

        PinVerificationOutcome incorrect = useCase.execute(cardNumber.getValue(), "9999");
        PinVerificationOutcome locked = useCase.execute(lockedCardNumber.getValue(), "1234");

        assertNull(incorrect.atmSessionId());
        assertNull(locked.atmSessionId());
        assertTrue(atmSessionRepository.all().isEmpty());
    }
}
