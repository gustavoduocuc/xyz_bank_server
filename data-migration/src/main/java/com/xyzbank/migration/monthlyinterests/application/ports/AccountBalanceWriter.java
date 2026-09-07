package com.xyzbank.migration.monthlyinterests.application.ports;

import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;

import java.util.List;

public interface AccountBalanceWriter {

    void write(List<InterestApplied> balances);
}
