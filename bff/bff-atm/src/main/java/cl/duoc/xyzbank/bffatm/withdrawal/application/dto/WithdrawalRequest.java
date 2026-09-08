package cl.duoc.xyzbank.bffatm.withdrawal.application.dto;

import java.math.BigDecimal;

public record WithdrawalRequest(BigDecimal amount, String currency) {
}
