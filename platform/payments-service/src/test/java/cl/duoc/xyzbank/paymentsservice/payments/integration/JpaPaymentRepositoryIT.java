package cl.duoc.xyzbank.paymentsservice.payments.integration;

import cl.duoc.xyzbank.paymentsservice.payments.domain.Payment;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentRepository;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentStatus;
import cl.duoc.xyzbank.paymentsservice.payments.domain.PaymentType;
import cl.duoc.xyzbank.paymentsservice.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The JPA payment repository")
class JpaPaymentRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Keeps its table and Flyway history in the payments schema
     * 2. Creates a payment that can be found by id and by idempotency key, and saves its new status
     * 3. Returns the first payment when the idempotency key is already taken
     */

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("keeps its table and Flyway history in the payments schema")
    void keepsItsTableAndFlywayHistoryInThePaymentsSchema() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'payments' AND table_name IN ('payments', 'flyway_schema_history')
                """, Integer.class);

        assertEquals(2, tables);
    }

    @Test
    @DisplayName("creates a payment found by id and key, and saves its new status")
    void createsAPaymentFoundByIdAndKeyAndSavesItsNewStatus() {
        String key = "repo-" + UUID.randomUUID();
        Payment payment = aTransfer(key);

        repository.create(payment);
        repository.update(payment.complete(NOW.plusSeconds(1)));

        Payment stored = repository.findById(payment.id()).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, stored.status());
        assertEquals(payment.sourceAccountId(), stored.sourceAccountId());
        assertEquals(0, new BigDecimal("100.00").compareTo(stored.amount()));
        assertEquals(NOW, stored.createdAt());
        assertEquals(NOW.plusSeconds(1), stored.updatedAt());
        assertEquals(payment.id(), repository.findByIdempotencyKey(key).orElseThrow().id());
    }

    @Test
    @DisplayName("returns the first payment when the idempotency key is already taken")
    void returnsTheFirstPaymentWhenTheIdempotencyKeyIsAlreadyTaken() {
        String key = "repo-" + UUID.randomUUID();
        Payment first = repository.create(aTransfer(key));

        Payment second = repository.create(aTransfer(key));

        assertEquals(first.id(), second.id());
        assertTrue(repository.findByIdempotencyKey(key).isPresent());
    }

    private static Payment aTransfer(String key) {
        return Payment.create(UUID.randomUUID(), PaymentType.TRANSFER, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", key, NOW);
    }
}
