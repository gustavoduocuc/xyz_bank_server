package cl.duoc.xyzbank.coreservice.auth.application.usecases;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.AtmSession;
import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.AtmSessionRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.cards.domain.services.PinHasher;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.dto.PinVerificationOutcome;

import java.time.Clock;

public class VerifyPinUseCase {

    private final CardRepository cardRepository;
    private final PinHasher pinHasher;
    private final AtmSessionRepository atmSessionRepository;
    private final Clock clock;

    public VerifyPinUseCase(
            CardRepository cardRepository, PinHasher pinHasher, AtmSessionRepository atmSessionRepository, Clock clock) {
        this.cardRepository = cardRepository;
        this.pinHasher = pinHasher;
        this.atmSessionRepository = atmSessionRepository;
        this.clock = clock;
    }

    public PinVerificationOutcome execute(String cardNumber, String pin) {
        return cardRepository.findByCardNumber(Id.create(cardNumber))
                .map(card -> verify(card, pin))
                .orElse(new PinVerificationOutcome(PinVerificationOutcome.Result.INCORRECT, null, null));
    }

    private PinVerificationOutcome verify(Card card, String pin) {
        Card.PinVerificationResult cardResult = card.verifyPin(pin, pinHasher);
        cardRepository.save(card);
        PinVerificationOutcome.Result result = toOutcomeResult(cardResult);
        if (result != PinVerificationOutcome.Result.SUCCESS) {
            return new PinVerificationOutcome(result, null, null);
        }
        // The verified PIN opens the ATM session core-service will act on for 120 seconds
        AtmSession session = AtmSession.open(card.getCustomerId(), card.getId(), clock.instant());
        atmSessionRepository.save(session);
        return new PinVerificationOutcome(result, card.getCustomerId().getValue(), session.getId().getValue());
    }

    private static PinVerificationOutcome.Result toOutcomeResult(Card.PinVerificationResult cardResult) {
        return switch (cardResult) {
            case SUCCESS -> PinVerificationOutcome.Result.SUCCESS;
            case INCORRECT -> PinVerificationOutcome.Result.INCORRECT;
            case LOCKED -> PinVerificationOutcome.Result.LOCKED;
        };
    }
}
