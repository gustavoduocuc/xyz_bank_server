package cl.duoc.xyzbank.coreservice.interests.application.dto;

import java.math.BigDecimal;

public record InterestCreditResponse(
        String transactionId,
        String accountId,
        int year,
        BigDecimal amount,
        String currency,
        String occurredOn,
        BigDecimal newBalance) {
}
