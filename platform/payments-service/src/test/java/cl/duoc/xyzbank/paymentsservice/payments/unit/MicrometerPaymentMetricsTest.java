package cl.duoc.xyzbank.paymentsservice.payments.unit;

import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;
import cl.duoc.xyzbank.paymentsservice.payments.infrastructure.metrics.MicrometerPaymentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The Micrometer payment metrics")
class MicrometerPaymentMetricsTest {

    /*
     * Cases (observability spec, "Payments are counted by status"):
     * 1. Each status entered increments payments.processed under its own status tag, and only that one
     */

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerPaymentMetrics metrics = new MicrometerPaymentMetrics(registry);

    @Test
    @DisplayName("counts each status under its own tag")
    void countsEachStatusUnderItsOwnTag() {
        metrics.statusEntered(PaymentStatus.PENDING);
        metrics.statusEntered(PaymentStatus.COMPLETED);
        metrics.statusEntered(PaymentStatus.COMPLETED);

        assertEquals(1.0, registry.counter("payments.processed", "status", "PENDING").count());
        assertEquals(2.0, registry.counter("payments.processed", "status", "COMPLETED").count());
        assertEquals(0.0, registry.counter("payments.processed", "status", "REJECTED").count());
    }
}
