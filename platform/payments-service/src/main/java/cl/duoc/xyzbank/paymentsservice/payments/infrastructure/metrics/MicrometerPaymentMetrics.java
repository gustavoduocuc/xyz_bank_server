package cl.duoc.xyzbank.paymentsservice.payments.infrastructure.metrics;

import cl.duoc.xyzbank.paymentsservice.payments.application.PaymentMetrics;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;

/** payments.processed{status}: one increment each time a payment enters a status. */
public class MicrometerPaymentMetrics implements PaymentMetrics {

    private final MeterRegistry registry;

    public MicrometerPaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void statusEntered(PaymentStatus status) {
        registry.counter("payments.processed", "status", status.name()).increment();
    }
}
