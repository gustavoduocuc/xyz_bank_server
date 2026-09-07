package cl.duoc.xyzbank.coreservice.accounts.application.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        String id,
        String accountNumber,
        BigDecimal balance,
        String currency) {
}
