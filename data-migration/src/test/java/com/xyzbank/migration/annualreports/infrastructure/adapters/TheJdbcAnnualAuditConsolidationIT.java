package com.xyzbank.migration.annualreports.infrastructure.adapters;

import com.xyzbank.migration.annualreports.domain.AnnualMovement;
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
class TheJdbcAnnualAuditConsolidationIT {

    /*
     * Cases:
     * 1. Writes one row per account with the summed totals and the movement count
     * 2. Rebuilding twice gives the same totals
     * 3. Includes movements stored by separate writes, as after a restart
     */

    @Nested
    class TheJdbcAnnualAuditConsolidation {

        private JdbcTemplate jdbcTemplate;
        private JdbcAnnualMovementStore store;
        private JdbcAnnualAuditConsolidation consolidation;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            store = new JdbcAnnualMovementStore(jdbcTemplate);
            consolidation = new JdbcAnnualAuditConsolidation(jdbcTemplate);
        }

        @Test
        void writesOneRowPerAccountWithTheSummedTotalsAndTheMovementCount() {
            store.write(List.of(
                    movement("101", "2024-01-01", "deposito", 1000, 2),
                    movement("101", "2024-03-15", "retiro", -500, 3),
                    movement("104", "2024-09-05", "compra", -100, 4)));

            consolidation.rebuild();

            Map<String, Object> account = audit("101");
            assertEquals(new BigDecimal("1000.00"), account.get("total_deposits"));
            assertEquals(new BigDecimal("500.00"), account.get("total_withdrawals"));
            assertEquals(new BigDecimal("500.00"), account.get("net_balance"));
            assertEquals(2, account.get("movement_count"));
            assertEquals(2, rows());
        }

        @Test
        void rebuildingTwiceGivesTheSameTotals() {
            store.write(List.of(movement("101", "2024-01-01", "deposito", 1000, 2)));

            consolidation.rebuild();
            consolidation.rebuild();

            assertEquals(new BigDecimal("1000.00"), audit("101").get("total_deposits"));
            assertEquals(1, audit("101").get("movement_count"));
        }

        @Test
        void includesMovementsStoredBySeparateWritesAsAfterARestart() {
            store.write(List.of(movement("101", "2024-01-01", "deposito", 1000, 2)));
            consolidation.rebuild();
            store.write(List.of(movement("101", "2024-02-01", "deposito", 250, 40)));

            consolidation.rebuild();

            assertEquals(new BigDecimal("1250.00"), audit("101").get("total_deposits"));
            assertEquals(2, audit("101").get("movement_count"));
        }

        private AnnualMovement movement(String accountId, String date, String type, double amount, int line) {
            return AnnualMovement.create(accountId, date, type, amount, "Movimiento", SourceLine.of(line));
        }

        private Map<String, Object> audit(String accountId) {
            return jdbcTemplate.queryForMap("SELECT * FROM annual_audit_reports WHERE account_id = ?", accountId);
        }

        private int rows() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM annual_audit_reports", Integer.class);
            return rows == null ? 0 : rows;
        }
    }
}
