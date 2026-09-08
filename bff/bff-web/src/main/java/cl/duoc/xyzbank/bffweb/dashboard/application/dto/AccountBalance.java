package cl.duoc.xyzbank.bffweb.dashboard.application.dto;

import java.math.BigDecimal;

public record AccountBalance(String id, String accountNumber, BigDecimal balance, String currency) {
}
