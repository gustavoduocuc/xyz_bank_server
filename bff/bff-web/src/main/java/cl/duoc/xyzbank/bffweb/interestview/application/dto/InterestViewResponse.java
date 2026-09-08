package cl.duoc.xyzbank.bffweb.interestview.application.dto;

import java.math.BigDecimal;

public record InterestViewResponse(
        String accountId,
        int year,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal interestRate,
        BigDecimal interestAmount,
        String currency) {
}
