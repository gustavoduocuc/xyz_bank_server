package cl.duoc.xyzbank.coreservice.withdrawals.application.dto;

import java.math.BigDecimal;

public record WithdrawRequest(
        String accountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {
}
