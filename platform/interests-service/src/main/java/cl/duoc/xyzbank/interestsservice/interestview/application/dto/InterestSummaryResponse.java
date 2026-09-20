package cl.duoc.xyzbank.interestsservice.interestview.application.dto;

import java.math.BigDecimal;

public record InterestSummaryResponse(
        String accountId,
        int year,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal interestRate,
        BigDecimal interestAmount,
        String currency) {
}
