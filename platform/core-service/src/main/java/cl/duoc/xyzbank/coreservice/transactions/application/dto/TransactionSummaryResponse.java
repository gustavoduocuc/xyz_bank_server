package cl.duoc.xyzbank.coreservice.transactions.application.dto;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
        String id,
        String type,
        BigDecimal amount,
        String currency,
        String occurredOn,
        String description) {
}
