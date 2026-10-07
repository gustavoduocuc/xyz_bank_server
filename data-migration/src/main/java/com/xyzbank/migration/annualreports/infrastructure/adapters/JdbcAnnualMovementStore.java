package com.xyzbank.migration.annualreports.infrastructure.adapters;

import com.xyzbank.migration.annualreports.application.ports.AnnualMovementStore;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import com.xyzbank.migration.shared.infrastructure.adapters.FirstWinsUpsert;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.List;

/**
 * Stores each accepted movement with its contribution to every annual total, keyed by
 * its business key, so a chunk can be rewritten and a restarted job keeps the movements
 * committed before the failure.
 */
public class JdbcAnnualMovementStore implements AnnualMovementStore {

    private static final String upsert = FirstWinsUpsert.statement(
            "annual_movements",
            "movement_key",
            "SHA2(?, 256)",
            List.of(
                    "account_id",
                    "movement_date",
                    "movement_type",
                    "amount",
                    "description",
                    "deposit_amount",
                    "withdrawal_amount",
                    "net_amount"
            )
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnnualMovementStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<AnnualMovement> movements) {
        List<Object[]> rows = movements.stream()
                .map(movement -> new Object[]{
                        movement.businessKey(),
                        movement.accountIdValue(),
                        Date.valueOf(movement.date().asIso()),
                        movement.type().name(),
                        movement.amount().amount(),
                        movement.description(),
                        movement.depositContribution().amount(),
                        movement.withdrawalContribution().amount(),
                        movement.netContribution().amount(),
                        movement.sourceLine().number()
                })
                .toList();
        jdbcTemplate.batchUpdate(upsert, rows);
    }
}
