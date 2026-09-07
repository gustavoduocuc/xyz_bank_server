package com.xyzbank.migration.monthlyinterests.infrastructure.adapters;

import com.xyzbank.migration.monthlyinterests.application.ports.AccountBalanceWriter;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcAccountBalanceWriter implements AccountBalanceWriter {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountBalanceWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<InterestApplied> balances) {
        for (InterestApplied applied : balances) {
            jdbcTemplate.update(
                    """
                            INSERT INTO account_balances
                                (account_id, account_name, account_type, age, previous_balance, interest_rate, final_balance)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    applied.accountIdValue(),
                    applied.accountName(),
                    applied.accountType().name(),
                    applied.accountAge(),
                    applied.previousBalanceValue(),
                    applied.rate(),
                    applied.finalBalanceValue()
            );
        }
    }
}
