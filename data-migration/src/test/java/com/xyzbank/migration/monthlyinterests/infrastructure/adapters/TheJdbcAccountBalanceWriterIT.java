package com.xyzbank.migration.monthlyinterests.infrastructure.adapters;

import com.xyzbank.migration.monthlyinterests.domain.Account;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.monthlyinterests.domain.InterestRatePolicy;
import com.xyzbank.migration.shared.domain.SourceLine;
import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TheJdbcAccountBalanceWriterIT {

    /*
     * Cases:
     * 1. Persists account balances with applied interest
     */

    @Nested
    class TheJdbcAccountBalanceWriter {

        private JdbcTemplate jdbcTemplate;
        private JdbcAccountBalanceWriter writer;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            writer = new JdbcAccountBalanceWriter(jdbcTemplate);
        }

        @Test
        void persistsAccountBalancesWithAppliedInterest() {
            InterestApplied applied = InterestRatePolicy.apply(
                    Account.create("101", "John Doe", 5000, 30, "ahorro", SourceLine.of(1))
            );

            writer.write(List.of(applied));

            Double finalBalance = jdbcTemplate.queryForObject(
                    "SELECT final_balance FROM account_balances WHERE account_id = '101'",
                    Double.class
            );
            assertEquals(5050.0, finalBalance);
        }
    }
}
