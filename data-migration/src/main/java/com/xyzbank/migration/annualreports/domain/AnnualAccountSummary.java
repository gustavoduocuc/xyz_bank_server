package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;

public final class AnnualAccountSummary {

    private final Id accountId;
    private final Money totalDeposits;
    private final Money totalWithdrawals;
    private final Money netBalance;
    private final int movementCount;

    public AnnualAccountSummary(
            Id accountId,
            Money totalDeposits,
            Money totalWithdrawals,
            Money netBalance,
            int movementCount
    ) {
        this.accountId = accountId;
        this.totalDeposits = totalDeposits;
        this.totalWithdrawals = totalWithdrawals;
        this.netBalance = netBalance;
        this.movementCount = movementCount;
    }

    public Id accountId() {
        return accountId;
    }

    public String accountIdValue() {
        return accountId.value();
    }

    public Money totalDeposits() {
        return totalDeposits;
    }

    public double totalDepositsValue() {
        return totalDeposits.amount();
    }

    public Money totalWithdrawals() {
        return totalWithdrawals;
    }

    public double totalWithdrawalsValue() {
        return totalWithdrawals.amount();
    }

    public Money netBalance() {
        return netBalance;
    }

    public double netBalanceValue() {
        return netBalance.amount();
    }

    public int movementCount() {
        return movementCount;
    }
}
