package cl.duoc.xyzbank.coreservice.accounts.application.dto;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;

import java.math.BigDecimal;

public record AccountResponse(
        String id,
        String accountNumber,
        String customerId,
        BigDecimal balance,
        String currency,
        String status,
        String alias,
        BigDecimal dailyWithdrawalLimit,
        long version) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId().getValue(),
                account.getAccountNumber().getValue(),
                account.getCustomerId().getValue(),
                account.getBalance().getAmount(),
                account.getBalance().getCurrency(),
                account.getStatus().name(),
                account.getAlias().orElse(null),
                account.getDailyWithdrawalLimit().map(Money::getAmount).orElse(null),
                account.getVersion());
    }
}
