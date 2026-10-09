package cl.duoc.xyzbank.paymentsservice.payments.application;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;

/** Reports that a payment entered a status; implemented over the metrics registry. */
public interface PaymentMetrics {

    void statusEntered(PaymentStatus status);
}
