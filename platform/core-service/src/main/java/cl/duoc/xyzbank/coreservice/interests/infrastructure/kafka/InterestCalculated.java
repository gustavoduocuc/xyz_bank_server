package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import java.math.BigDecimal;

public record InterestCalculated(
        String eventId,
        String eventType,
        int schemaVersion,
        String accountId,
        int period,
        BigDecimal amount,
        String currency,
        BigDecimal interestRate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        String occurredAt) {
}
