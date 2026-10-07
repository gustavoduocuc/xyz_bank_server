package com.xyzbank.migration.annualreports.infrastructure.adapters;

import com.xyzbank.migration.annualreports.domain.AnnualAccountCompiler;
import com.xyzbank.migration.annualreports.domain.AnnualAccountSummary;
import com.xyzbank.migration.annualreports.domain.AnnualMovement;
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
class TheJdbcAnnualAuditWriterIT {

    /*
     * Cases:
     * 1. Persists annual audit summaries
     */

    @Nested
    class TheJdbcAnnualAuditWriter {

        private JdbcTemplate jdbcTemplate;
        private JdbcAnnualAuditWriter writer;

        @BeforeEach
        void setUp() {
            jdbcTemplate = MySqlContainerSupport.freshJdbcTemplate();
            writer = new JdbcAnnualAuditWriter(jdbcTemplate);
        }

        @Test
        void persistsAnnualAuditSummaries() {
            List<AnnualAccountSummary> summaries = AnnualAccountCompiler.compile(List.of(
                    AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso mensual", SourceLine.of(1)),
                    AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro parcial", SourceLine.of(1))
            ));

            writer.write(summaries);

            Double net = jdbcTemplate.queryForObject(
                    "SELECT net_balance FROM annual_audit_reports WHERE account_id = '101'",
                    Double.class
            );
            assertEquals(500.0, net);
        }
    }
}
