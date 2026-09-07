package cl.duoc.xyzbank.coreservice.interests.application.dto;

import java.math.BigDecimal;

public record AnnualInterestSummaryResponse(
        String accountId,
        int year,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal interestRate,
        BigDecimal interestAmount,
        String currency) {
}
