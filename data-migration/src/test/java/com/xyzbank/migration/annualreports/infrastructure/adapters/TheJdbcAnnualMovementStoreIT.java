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
class TheJdbcAnnualMovementStoreIT {

    /*
     * Cases:
     * 1. Stores a movement keyed by the SHA-256 of its business key, with its contributions
     * 2. Writing the same chunk twice leaves the same rows
     * 3. The same business key from a lower line keeps the lowest source line
     */

    @Nested
    class TheJdbcAnnualMovementStore {

        private JdbcTemplate jdbcTemplate;
        private JdbcAnnualMovementStore store;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            store = new JdbcAnnualMovementStore(jdbcTemplate);
        }

        @Test
        void storesAMovementKeyedByTheSha256OfItsBusinessKeyWithItsContributions() {
            AnnualMovement withdrawal = movement("101", "2024-03-15", "retiro", -500, 7);

            store.write(List.of(withdrawal));

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM annual_movements WHERE movement_key = SHA2(?, 256)", withdrawal.businessKey());
            assertEquals("101", row.get("account_id"));
            assertEquals("WITHDRAWAL", row.get("movement_type"));
            assertEquals(new BigDecimal("0.00"), row.get("deposit_amount"));
            assertEquals(new BigDecimal("500.00"), row.get("withdrawal_amount"));
            assertEquals(new BigDecimal("-500.00"), row.get("net_amount"));
            assertEquals(7, row.get("source_line"));
        }

        @Test
        void writingTheSameChunkTwiceLeavesTheSameRows() {
            List<AnnualMovement> chunk = List.of(
                    movement("101", "2024-01-01", "deposito", 1000, 2),
                    movement("101", "2024-03-15", "retiro", -500, 3));

            store.write(chunk);
            store.write(chunk);

            assertEquals(2, rows());
        }

        @Test
        void theSameBusinessKeyFromALowerLineKeepsTheLowestSourceLine() {
            store.write(List.of(movement("101", "2024-01-01", "deposito", 1000, 90)));

            store.write(List.of(movement("101", "2024-01-01", "deposito", 1000, 4)));

            assertEquals(1, rows());
            assertEquals(4, jdbcTemplate.queryForObject("SELECT source_line FROM annual_movements", Integer.class));
        }

        private AnnualMovement movement(String accountId, String date, String type, double amount, int line) {
            return AnnualMovement.create(accountId, date, type, amount, "Movimiento", SourceLine.of(line));
        }

        private int rows() {
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM annual_movements", Integer.class);
            return rows == null ? 0 : rows;
        }
    }
}
