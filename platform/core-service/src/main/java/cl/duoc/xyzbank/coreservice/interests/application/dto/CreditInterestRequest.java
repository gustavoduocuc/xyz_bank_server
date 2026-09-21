package cl.duoc.xyzbank.coreservice.interests.application.dto;

import java.math.BigDecimal;

public record CreditInterestRequest(
        String accountId,
        int year,
        BigDecimal amount,
        String currency,
        BigDecimal interestRate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        String idempotencyKey) {
}
