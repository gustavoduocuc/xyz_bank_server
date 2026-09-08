package cl.duoc.xyzbank.bffatm.balanceinquiry.application.dto;

import java.math.BigDecimal;

public record BalanceResponse(BigDecimal balance, String currency) {
}
