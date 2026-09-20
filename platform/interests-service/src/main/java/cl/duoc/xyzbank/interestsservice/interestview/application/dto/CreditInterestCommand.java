package cl.duoc.xyzbank.interestsservice.interestview.application.dto;

import java.math.BigDecimal;

public record CreditInterestCommand(
        String accountId,
        int year,
        BigDecimal amount,
        String currency,
        BigDecimal interestRate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        String idempotencyKey) {
}
