package cl.duoc.xyzbank.paymentsservice.payments.unit;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentException;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("A Payment")
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(1);

    /*
     * Cases:
     * 1. Is created PENDING with its accounts, amount and key
     * 2. A transfer needs two different accounts; a deposit only a destination; a bill payment only a source
     * 3. Rejects a non-positive amount and a malformed currency
     * 4. Completes or is rejected only from PENDING, stamping updatedAt
     */

    @Test
    @DisplayName("is created PENDING with its accounts, amount and key")
    void isCreatedPending() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();

        Payment payment = Payment.create(UUID.randomUUID(), PaymentType.TRANSFER, source, destination,
                new BigDecimal("100.00"), "USD", "t-1", NOW);

        assertEquals(PaymentStatus.PENDING, payment.status());
        assertEquals(Optional.of(source), payment.sourceAccountId());
        assertEquals(Optional.of(destination), payment.destinationAccountId());
        assertEquals("t-1", payment.idempotencyKey());
        assertEquals(NOW, payment.createdAt());
        assertEquals(NOW, payment.updatedAt());
    }

    @Test
    @DisplayName("requires the accounts its type moves money between")
    void requiresTheAccountsItsTypeMovesMoneyBetween() {
        UUID account = UUID.randomUUID();

        assertInvalid(() -> create(PaymentType.TRANSFER, account, account));
        assertInvalid(() -> create(PaymentType.TRANSFER, account, null));
        assertInvalid(() -> create(PaymentType.DEPOSIT, account, UUID.randomUUID()));
        assertInvalid(() -> create(PaymentType.DEPOSIT, null, null));
        assertInvalid(() -> create(PaymentType.BILL_PAYMENT, account, UUID.randomUUID()));
        assertInvalid(() -> create(PaymentType.BILL_PAYMENT, null, null));
        create(PaymentType.DEPOSIT, null, account);
        create(PaymentType.BILL_PAYMENT, account, null);
    }

    @Test
    @DisplayName("rejects a non-positive amount and a malformed currency")
    void rejectsANonPositiveAmountAndAMalformedCurrency() {
        UUID account = UUID.randomUUID();

        assertInvalid(() -> Payment.create(UUID.randomUUID(), PaymentType.DEPOSIT, null, account,
                BigDecimal.ZERO, "USD", "k", NOW));
        assertInvalid(() -> Payment.create(UUID.randomUUID(), PaymentType.DEPOSIT, null, account,
                null, "USD", "k", NOW));
        assertInvalid(() -> Payment.create(UUID.randomUUID(), PaymentType.DEPOSIT, null, account,
                BigDecimal.TEN, "usd", "k", NOW));
        assertInvalid(() -> Payment.create(UUID.randomUUID(), PaymentType.DEPOSIT, null, account,
                BigDecimal.TEN, null, "k", NOW));
    }

    @Test
    @DisplayName("completes or is rejected only from PENDING")
    void completesOrIsRejectedOnlyFromPending() {
        Payment pending = create(PaymentType.DEPOSIT, null, UUID.randomUUID());

        Payment completed = pending.complete(LATER);
        Payment rejected = pending.reject(LATER);

        assertEquals(PaymentStatus.COMPLETED, completed.status());
        assertEquals(LATER, completed.updatedAt());
        assertEquals(PaymentStatus.REJECTED, rejected.status());
        PaymentException settledAgain = assertThrows(PaymentException.class, () -> completed.reject(LATER));
        assertEquals(PaymentException.Type.CONFLICT, settledAgain.type());
        assertThrows(PaymentException.class, () -> rejected.complete(LATER));
    }

    private static Payment create(PaymentType type, UUID source, UUID destination) {
        return Payment.create(UUID.randomUUID(), type, source, destination, new BigDecimal("10.00"), "USD", "k", NOW);
    }

    private static void assertInvalid(Runnable creation) {
        PaymentException exception = assertThrows(PaymentException.class, creation::run);
        assertEquals(PaymentException.Type.VALIDATION, exception.type());
    }
}
