package cl.duoc.xyzbank.bffatm.withdrawal.application.dto;

import java.math.BigDecimal;

public record WithdrawalResponse(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String occurredOn,
        BigDecimal newBalance) {
}
