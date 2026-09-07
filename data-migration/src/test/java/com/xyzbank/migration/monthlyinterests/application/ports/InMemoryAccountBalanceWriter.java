package com.xyzbank.migration.monthlyinterests.application.ports;

import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAccountBalanceWriter implements AccountBalanceWriter {

    private final List<InterestApplied> written = new ArrayList<>();

    @Override
    public void write(List<InterestApplied> balances) {
        written.addAll(balances);
    }

    public List<InterestApplied> written() {
        return List.copyOf(written);
    }
}
