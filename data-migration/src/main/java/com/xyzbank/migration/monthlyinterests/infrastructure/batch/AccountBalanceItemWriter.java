package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

public class AccountBalanceItemWriter implements ItemWriter<InterestApplied> {

    private final AccountBalanceWriter accountBalanceWriter;

    public AccountBalanceItemWriter(AccountBalanceWriter accountBalanceWriter) {
        this.accountBalanceWriter = accountBalanceWriter;
    }

    @Override
    public void write(@NonNull Chunk<? extends InterestApplied> chunk) {
        List<InterestApplied> balances = new ArrayList<>(chunk.getItems());
        accountBalanceWriter.write(balances);
    }
}
