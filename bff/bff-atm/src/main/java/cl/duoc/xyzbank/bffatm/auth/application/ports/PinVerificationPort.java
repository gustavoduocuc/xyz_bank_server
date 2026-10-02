package cl.duoc.xyzbank.bffatm.auth.application.ports;

import cl.duoc.xyzbank.bffatm.auth.application.dto.VerifiedPin;

/**
 * Asks core-service to verify a card PIN. Each call is at most one attempt: core-service counts
 * failed attempts and locks the card at three, so a verification is never repeated on the
 * terminal's behalf (bff-resilience spec).
 */
public interface PinVerificationPort {

    VerifiedPin verify(String cardNumber, String pin);
}
