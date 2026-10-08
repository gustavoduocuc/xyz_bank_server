package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;

public interface PostingGateway {

    /**
     * Asks core-service to apply the payment's entries; core-service applies a payment at most once.
     *
     * @throws CoreUnavailableException when core-service cannot answer
     */
    PostingOutcome post(Payment payment);
}
