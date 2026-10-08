package cl.duoc.xyzbank.paymentsservice.payments.unit;

import cl.duoc.xyzbank.paymentsservice.payments.application.GetPaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentResponse;
import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The GetPayment use case")
class GetPaymentUseCaseTest {

    private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
    private final GetPaymentUseCase useCase = new GetPaymentUseCase(paymentRepository);

    /*
     * Cases:
     * 1. Returns a recorded payment
     * 2. Throws not found for an unknown id
     * 3. Throws validation for an id that is not a UUID
     */

    @Test
    @DisplayName("returns a recorded payment")
    void returnsARecordedPayment() {
        Payment payment = Payment.create(UUID.randomUUID(), PaymentType.DEPOSIT, null, UUID.randomUUID(),
                new BigDecimal("10.00"), "USD", "k", Instant.parse("2026-10-07T10:00:00Z"));
        paymentRepository.create(payment);

        PaymentResponse response = useCase.execute(payment.id().toString());

        assertEquals(payment.id().toString(), response.id());
        assertEquals("PENDING", response.status());
        assertEquals("DEPOSIT", response.type());
    }

    @Test
    @DisplayName("throws not found for an unknown id")
    void throwsNotFoundForAnUnknownId() {
        PaymentException exception = assertThrows(PaymentException.class,
                () -> useCase.execute(UUID.randomUUID().toString()));

        assertEquals(PaymentException.Type.NOT_FOUND, exception.type());
    }

    @Test
    @DisplayName("throws validation for an id that is not a UUID")
    void throwsValidationForAnIdThatIsNotAUuid() {
        PaymentException exception = assertThrows(PaymentException.class, () -> useCase.execute("nope"));

        assertEquals(PaymentException.Type.VALIDATION, exception.type());
    }
}
