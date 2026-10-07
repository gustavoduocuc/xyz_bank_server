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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TheJdbcAccountBalanceWriterIT {

    /*
     * Cases:
     * 1. Persists account balances with applied interest and their source line
     * 2. Writing the same chunk twice leaves the same rows
     * 3. The same account from a lower line replaces the stored row
     * 4. The same account from a higher line is ignored
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
        void persistsAccountBalancesWithAppliedInterestAndTheirSourceLine() {
            writer.write(List.of(applied("101", 5000, 12)));

            Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM account_balances WHERE account_id = '101'");
            assertEquals(new BigDecimal("5050.00"), row.get("final_balance"));
            assertEquals(new BigDecimal("0.0100"), row.get("interest_rate"));
            assertEquals(12, row.get("source_line"));
        }

        @Test
        void writingTheSameChunkTwiceLeavesTheSameRows() {
            List<InterestApplied> chunk = List.of(applied("101", 5000, 2), applied("102", 8000, 3));

            writer.write(chunk);
            writer.write(chunk);

            assertEquals(2, rows());
        }

        @Test
        void theSameAccountFromALowerLineReplacesTheStoredRow() {
            writer.write(List.of(applied("137", 9000, 880)));

            writer.write(List.of(applied("137", 7000, 12)));

            assertEquals(new BigDecimal("7000.00"), previousBalanceOf("137"));
            assertEquals(1, rows());
        }

        @Test
        void theSameAccountFromAHigherLineIsIgnored() {
            writer.write(List.of(applied("137", 7000, 12)));

            writer.write(List.of(applied("137", 9000, 880)));

            assertEquals(new BigDecimal("7000.00"), previousBalanceOf("137"));
            assertEquals(1, rows());
        }

        private InterestApplied applied(String accountId, double balance, int line) {
            return InterestRatePolicy.apply(
                    Account.create(accountId, "John Doe", balance, 30, "ahorro", SourceLine.of(line)));
        }

        private BigDecimal previousBalanceOf(String accountId) {
            return jdbcTemplate.queryForObject(
                    "SELECT previous_balance FROM account_balances WHERE account_id = ?", BigDecimal.class, accountId);
        }

        private int rows() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_balances", Integer.class);
            return rows == null ? 0 : rows;
        }
    }
}
