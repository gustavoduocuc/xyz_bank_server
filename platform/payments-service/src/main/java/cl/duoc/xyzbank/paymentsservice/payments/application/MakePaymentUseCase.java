package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a payment PENDING under its Idempotency-Key, then asks core-service to apply it, outside
 * any database transaction. Repeating a key returns that key's payment; while it is still PENDING
 * the posting is attempted again, which is safe because core-service applies a payment at most once.
 */
public class MakePaymentUseCase {

    private static final int MAX_KEY_LENGTH = 64;

    private final PaymentRepository paymentRepository;
    private final PostingGateway postingGateway;
    private final Clock clock;

    public MakePaymentUseCase(PaymentRepository paymentRepository, PostingGateway postingGateway, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.postingGateway = postingGateway;
        this.clock = clock;
    }

    public PaymentResponse execute(String idempotencyKey, PaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw PaymentException.validation("Idempotency-Key header is required, up to 64 characters");
        }
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> paymentRepository.create(newPayment(idempotencyKey, request)));
        if (payment.status() != PaymentStatus.PENDING) {
            return PaymentResponse.from(payment);
        }

        PostingOutcome outcome = postingGateway.post(payment);

        Instant now = Instant.now(clock);
        Payment settled = outcome == PostingOutcome.APPLIED ? payment.complete(now) : payment.reject(now);
        paymentRepository.update(settled);
        return PaymentResponse.from(settled);
    }

    private Payment newPayment(String idempotencyKey, PaymentRequest request) {
        if (request.type() == null) {
            throw PaymentException.validation("type is required");
        }
        return Payment.create(
                UUID.randomUUID(),
                request.type(),
                Uuids.parse("sourceAccountId", request.sourceAccountId()),
                Uuids.parse("destinationAccountId", request.destinationAccountId()),
                request.amount(),
                request.currency(),
                idempotencyKey,
                Instant.now(clock));
    }
}
