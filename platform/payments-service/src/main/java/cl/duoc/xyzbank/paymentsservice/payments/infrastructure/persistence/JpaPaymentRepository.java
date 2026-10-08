package cl.duoc.xyzbank.paymentsservice.payments.infrastructure.persistence;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Each write runs in its own short transaction (Spring Data's), so a losing duplicate insert
 * can re-read the winning row afterwards, and no transaction spans the call to core-service.
 */
@Repository
public class JpaPaymentRepository implements PaymentRepository {

    private final SpringDataPaymentRepository springData;

    public JpaPaymentRepository(SpringDataPaymentRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return springData.findById(id).map(JpaPaymentRepository::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return springData.findByIdempotencyKey(idempotencyKey).map(JpaPaymentRepository::toDomain);
    }

    @Override
    public Payment create(Payment payment) {
        try {
            return toDomain(springData.saveAndFlush(toEntity(payment)));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return findByIdempotencyKey(payment.idempotencyKey()).orElseThrow(() -> concurrentDuplicate);
        }
    }

    @Override
    public void update(Payment payment) {
        springData.saveAndFlush(toEntity(payment));
    }

    private static PaymentJpaEntity toEntity(Payment payment) {
        return new PaymentJpaEntity(payment.id(), payment.type().name(), payment.sourceAccountId(),
                payment.destinationAccountId(), payment.amount(), payment.currency(), payment.status().name(),
                payment.idempotencyKey(), payment.createdAt(), payment.updatedAt());
    }

    private static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(entity.getId(), PaymentType.valueOf(entity.getType()), entity.getSourceAccountId(),
                entity.getDestinationAccountId(), entity.getAmount(), entity.getCurrency(),
                PaymentStatus.valueOf(entity.getStatus()), entity.getIdempotencyKey(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
