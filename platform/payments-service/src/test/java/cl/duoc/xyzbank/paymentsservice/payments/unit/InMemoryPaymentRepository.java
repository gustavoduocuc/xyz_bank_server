package cl.duoc.xyzbank.paymentsservice.payments.unit;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<UUID, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(payments.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return payments.values().stream()
                .filter(payment -> payment.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public synchronized Payment create(Payment payment) {
        return findByIdempotencyKey(payment.idempotencyKey()).orElseGet(() -> {
            payments.put(payment.id(), payment);
            return payment;
        });
    }

    @Override
    public void update(Payment payment) {
        payments.put(payment.id(), payment);
    }

    public int size() {
        return payments.size();
    }
}
