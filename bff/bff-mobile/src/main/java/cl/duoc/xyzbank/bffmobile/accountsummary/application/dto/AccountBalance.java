package cl.duoc.xyzbank.bffmobile.accountsummary.application.dto;

import java.math.BigDecimal;

public record AccountBalance(BigDecimal balance, String currency) {
}
