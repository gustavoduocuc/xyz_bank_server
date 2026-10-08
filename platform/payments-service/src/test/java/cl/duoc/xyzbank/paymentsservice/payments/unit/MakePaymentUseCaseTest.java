package cl.duoc.xyzbank.paymentsservice.payments.unit;

import cl.duoc.xyzbank.paymentsservice.payments.application.CoreUnavailableException;
import cl.duoc.xyzbank.paymentsservice.payments.application.MakePaymentUseCase;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentRequest;
import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentResponse;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingGateway;
import cl.duoc.xyzbank.paymentsservice.payments.application.PostingOutcome;
import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The MakePayment use case")
class MakePaymentUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
    private static final String SOURCE = UUID.randomUUID().toString();
    private static final String DESTINATION = UUID.randomUUID().toString();

    private final InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
    private final StubPostingGateway gateway = new StubPostingGateway();
    private final MakePaymentUseCase useCase = new MakePaymentUseCase(paymentRepository, gateway, CLOCK);

    /*
     * Cases:
     * 1. Transfer, deposit and bill payment are COMPLETED when core-service applies them
     * 2. Transfer, deposit and bill payment are REJECTED when core-service refuses them
     * 3. Repeating the key of a COMPLETED payment returns it without posting again
     * 4. Repeating the key of a PENDING payment posts it again and completes it
     * 5. An unavailable core-service leaves the payment PENDING and propagates CoreUnavailableException
     * 6. A missing key, a non-positive amount, a bad currency or a transfer to the same account
     *    throws validation and records nothing
     */

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    @DisplayName("completes a payment core-service applies")
    void completesAPaymentCoreServiceApplies(PaymentType type) {
        gateway.answer(PostingOutcome.APPLIED);

        PaymentResponse response = useCase.execute("key-1", request(type));

        assertEquals("COMPLETED", response.status());
        assertEquals(type.name(), response.type());
        assertEquals(1, gateway.calls.size());
        assertEquals(response.id(), gateway.calls.get(0).id().toString());
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    @DisplayName("rejects a payment core-service refuses")
    void rejectsAPaymentCoreServiceRefuses(PaymentType type) {
        gateway.answer(PostingOutcome.REFUSED);

        PaymentResponse response = useCase.execute("key-1", request(type));

        assertEquals("REJECTED", response.status());
        assertEquals("REJECTED", paymentRepository.findByIdempotencyKey("key-1").orElseThrow().status().name());
    }

    @Test
    @DisplayName("returns a COMPLETED payment for a repeated key without posting again")
    void returnsACompletedPaymentForARepeatedKeyWithoutPostingAgain() {
        gateway.answer(PostingOutcome.APPLIED);

        PaymentResponse first = useCase.execute("key-1", request(PaymentType.TRANSFER));
        PaymentResponse second = useCase.execute("key-1", request(PaymentType.TRANSFER));

        assertEquals(first, second);
        assertEquals(1, gateway.calls.size());
        assertEquals(1, paymentRepository.size());
    }

    @Test
    @DisplayName("posts a PENDING payment again when its key is repeated")
    void postsAPendingPaymentAgainWhenItsKeyIsRepeated() {
        gateway.failWithUnavailable();
        assertThrows(CoreUnavailableException.class, () -> useCase.execute("key-1", request(PaymentType.TRANSFER)));
        gateway.answer(PostingOutcome.APPLIED);

        PaymentResponse response = useCase.execute("key-1", request(PaymentType.TRANSFER));

        assertEquals("COMPLETED", response.status());
        assertEquals(2, gateway.calls.size());
        assertEquals(gateway.calls.get(0).id(), gateway.calls.get(1).id());
        assertEquals(1, paymentRepository.size());
    }

    @Test
    @DisplayName("leaves the payment PENDING when core-service is unavailable")
    void leavesThePaymentPendingWhenCoreServiceIsUnavailable() {
        gateway.failWithUnavailable();

        assertThrows(CoreUnavailableException.class, () -> useCase.execute("key-1", request(PaymentType.DEPOSIT)));

        assertEquals("PENDING", paymentRepository.findByIdempotencyKey("key-1").orElseThrow().status().name());
    }

    @Test
    @DisplayName("throws validation for an invalid request and records nothing")
    void throwsValidationForAnInvalidRequestAndRecordsNothing() {
        assertInvalid(null, request(PaymentType.TRANSFER));
        assertInvalid(" ", request(PaymentType.TRANSFER));
        assertInvalid("k", new PaymentRequest(PaymentType.DEPOSIT, null, DESTINATION, BigDecimal.ZERO, "USD"));
        assertInvalid("k", new PaymentRequest(PaymentType.DEPOSIT, null, DESTINATION, BigDecimal.TEN, "US"));
        assertInvalid("k", new PaymentRequest(PaymentType.TRANSFER, SOURCE, SOURCE, BigDecimal.TEN, "USD"));
        assertInvalid("k", new PaymentRequest(PaymentType.DEPOSIT, null, "not-a-uuid", BigDecimal.TEN, "USD"));
        assertEquals(0, paymentRepository.size());
        assertEquals(0, gateway.calls.size());
    }

    private void assertInvalid(String key, PaymentRequest request) {
        PaymentException exception = assertThrows(PaymentException.class, () -> useCase.execute(key, request));
        assertEquals(PaymentException.Type.VALIDATION, exception.type());
    }

    private static PaymentRequest request(PaymentType type) {
        return switch (type) {
            case TRANSFER -> new PaymentRequest(type, SOURCE, DESTINATION, new BigDecimal("100.00"), "USD");
            case DEPOSIT -> new PaymentRequest(type, null, DESTINATION, new BigDecimal("100.00"), "USD");
            case BILL_PAYMENT -> new PaymentRequest(type, SOURCE, null, new BigDecimal("100.00"), "USD");
        };
    }

    private static final class StubPostingGateway implements PostingGateway {

        private final List<Payment> calls = new ArrayList<>();
        private PostingOutcome outcome = PostingOutcome.APPLIED;
        private boolean unavailable;

        void answer(PostingOutcome outcome) {
            this.outcome = outcome;
            this.unavailable = false;
        }

        void failWithUnavailable() {
            this.unavailable = true;
        }

        @Override
        public PostingOutcome post(Payment payment) {
            calls.add(payment);
            if (unavailable) {
                throw new CoreUnavailableException("core-service is unavailable", null);
            }
            return outcome;
        }
    }
}
