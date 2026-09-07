package com.xyzbank.migration.annualreports.infrastructure.adapters;

import com.xyzbank.migration.annualreports.application.ports.AnnualAuditWriter;
import com.xyzbank.migration.annualreports.domain.AnnualAccountSummary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcAnnualAuditWriter implements AnnualAuditWriter {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnnualAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(List<AnnualAccountSummary> summaries) {
        for (AnnualAccountSummary summary : summaries) {
            jdbcTemplate.update(
                    """
                            INSERT INTO annual_audit_reports
                                (account_id, total_deposits, total_withdrawals, net_balance, movement_count)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    summary.accountIdValue(),
                    summary.totalDepositsValue(),
                    summary.totalWithdrawalsValue(),
                    summary.netBalanceValue(),
                    summary.movementCount()
            );
        }
    }
}
