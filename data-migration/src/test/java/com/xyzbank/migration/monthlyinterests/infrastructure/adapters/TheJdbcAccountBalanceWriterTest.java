package com.xyzbank.migration.monthlyinterests.infrastructure.adapters;

import com.xyzbank.migration.monthlyinterests.domain.Account;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.monthlyinterests.domain.InterestRatePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TheJdbcAccountBalanceWriterTest {

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
            DataSource dataSource = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("classpath:db/schema.sql")
                    .build();
            jdbcTemplate = new JdbcTemplate(dataSource);
            writer = new JdbcAccountBalanceWriter(jdbcTemplate);
        }

        @Test
        void persistsAccountBalancesWithAppliedInterest() {
            InterestApplied applied = InterestRatePolicy.apply(
                    Account.create("101", "John Doe", 5000, 30, "ahorro")
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
