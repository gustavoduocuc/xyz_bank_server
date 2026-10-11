package com.xyzbank.migration.monthlyinterests.infrastructure.adapters;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.shared.infrastructure.adapters.FirstWinsUpsert;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcAccountBalanceWriter implements AccountBalanceWriter {

    private static final String upsert = FirstWinsUpsert.statement(
            "account_balances",
            "account_id",
            "?",
            List.of("account_name", "account_type", "age", "previous_balance", "interest_rate", "final_balance")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountBalanceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<InterestApplied> balances) {
        List<Object[]> rows = balances.stream()
                .map(applied -> new Object[]{
                        applied.accountIdValue(),
                        applied.accountName(),
                        applied.accountType().name(),
                        applied.accountAge(),
                        applied.previousBalanceValue(),
                        applied.rate(),
                        applied.finalBalanceValue(),
                        applied.sourceLine().number()
                })
                .toList();
        jdbcTemplate.batchUpdate(upsert, rows);
    }
}
