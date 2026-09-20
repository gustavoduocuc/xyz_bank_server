package cl.duoc.xyzbank.interestsservice.interestview.application.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        String accountId,
        BigDecimal balance,
        String currency) {
}
