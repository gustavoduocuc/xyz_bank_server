package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;

import java.math.BigDecimal;

public record PaymentRequest(
        PaymentType type,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency) {
}
