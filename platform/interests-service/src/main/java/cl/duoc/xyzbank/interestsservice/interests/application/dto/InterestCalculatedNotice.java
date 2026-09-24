package cl.duoc.xyzbank.interestsservice.interests.application.dto;

import java.math.BigDecimal;

public record InterestCalculatedNotice(
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
