package cl.duoc.xyzbank.paymentsservice.payments.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** Records a new payment; if its idempotency key is already taken, returns that key's payment. */
    Payment create(Payment payment);

    /** Saves the payment's new status. */
    void update(Payment payment);
}
