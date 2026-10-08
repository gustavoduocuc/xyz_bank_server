package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        String id,
        String type,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.id().toString(),
                payment.type().name(),
                text(payment.sourceAccountId()),
                text(payment.destinationAccountId()),
                payment.amount(),
                payment.currency(),
                payment.status().name(),
                payment.createdAt(),
                payment.updatedAt());
    }

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }
}
