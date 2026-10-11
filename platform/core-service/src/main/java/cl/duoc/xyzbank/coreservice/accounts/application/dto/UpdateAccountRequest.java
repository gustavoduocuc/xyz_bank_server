package cl.duoc.xyzbank.coreservice.accounts.application.dto;

import java.math.BigDecimal;

/** {@code version} is the one the caller last read; an omitted alias or limit keeps its value. */
public record UpdateAccountRequest(String alias, BigDecimal dailyWithdrawalLimit, Long version) {
}
