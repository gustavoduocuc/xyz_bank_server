package com.xyzbank.migration.annualreports.infrastructure.adapters;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditConsolidation;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sums the per-movement contributions computed by the domain; no business rule lives
 * in this SQL.
 */
public class JdbcAnnualAuditConsolidation implements AnnualAuditConsolidation {

    private static final String rebuild = """
            INSERT INTO annual_audit_reports
                (account_id, total_deposits, total_withdrawals, net_balance, movement_count)
            SELECT * FROM (
                SELECT account_id,
                       SUM(deposit_amount) AS total_deposits,
                       SUM(withdrawal_amount) AS total_withdrawals,
                       SUM(net_amount) AS net_balance,
                       COUNT(*) AS movement_count
                FROM annual_movements
                GROUP BY account_id
            ) AS rollup
            ON DUPLICATE KEY UPDATE
                total_deposits = rollup.total_deposits,
                total_withdrawals = rollup.total_withdrawals,
                net_balance = rollup.net_balance,
                movement_count = rollup.movement_count
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnnualAuditConsolidation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void rebuild() {
        jdbcTemplate.update(rebuild);
    }
}
