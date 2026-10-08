package cl.duoc.xyzbank.paymentsservice.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A transfer, deposit or bill payment. It is recorded PENDING and ends COMPLETED when
 * core-service applies its entries or REJECTED when core-service refuses them.
 */
public final class Payment {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final UUID id;
    private final PaymentType type;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Payment(
            UUID id,
            PaymentType type,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.type = type;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(
            UUID id,
            PaymentType type,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            Instant now) {
        requireAccountsOf(type, sourceAccountId, destinationAccountId);
        if (amount == null || amount.signum() <= 0) {
            throw PaymentException.validation("amount must be positive");
        }
        if (currency == null || !CURRENCY.matcher(currency).matches()) {
            throw PaymentException.validation("currency must be a three-letter code");
        }
        return new Payment(id, type, sourceAccountId, destinationAccountId, amount, currency,
                PaymentStatus.PENDING, idempotencyKey, now, now);
    }

    public static Payment restore(
            UUID id,
            PaymentType type,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt) {
        return new Payment(id, type, sourceAccountId, destinationAccountId, amount, currency, status,
                idempotencyKey, createdAt, updatedAt);
    }

    private static void requireAccountsOf(PaymentType type, UUID source, UUID destination) {
        boolean valid = switch (type) {
            case TRANSFER -> source != null && destination != null && !source.equals(destination);
            case DEPOSIT -> source == null && destination != null;
            case BILL_PAYMENT -> source != null && destination == null;
        };
        if (!valid) {
            throw PaymentException.validation(switch (type) {
                case TRANSFER -> "A transfer needs a sourceAccountId and a different destinationAccountId";
                case DEPOSIT -> "A deposit needs only a destinationAccountId";
                case BILL_PAYMENT -> "A bill payment needs only a sourceAccountId";
            });
        }
    }

    public Payment complete(Instant now) {
        return settle(PaymentStatus.COMPLETED, now);
    }

    public Payment reject(Instant now) {
        return settle(PaymentStatus.REJECTED, now);
    }

    private Payment settle(PaymentStatus outcome, Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment " + id + " is already " + status);
        }
        return new Payment(id, type, sourceAccountId, destinationAccountId, amount, currency, outcome,
                idempotencyKey, createdAt, now);
    }

    public UUID id() {
        return id;
    }

    public PaymentType type() {
        return type;
    }

    public UUID sourceAccountId() {
        return sourceAccountId;
    }

    public UUID destinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public PaymentStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Payment payment && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
