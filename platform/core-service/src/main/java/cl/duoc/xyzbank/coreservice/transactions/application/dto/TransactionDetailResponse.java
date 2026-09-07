package cl.duoc.xyzbank.coreservice.transactions.application.dto;

import java.math.BigDecimal;

public record TransactionDetailResponse(
        String id,
        String accountId,
        String type,
        BigDecimal amount,
        String currency,
        String occurredOn,
        String description) {
}
