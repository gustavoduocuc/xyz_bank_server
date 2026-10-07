package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.infrastructure.support.GoldenSnapshot;
import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "migration.batch.chunk-size=50",
        "migration.batch.throttle-limit=4",
        "migration.batch.skip-limit=2000",
        "migration.data.daily-transactions=file:data/semana_3/transacciones.csv",
        "migration.data.monthly-interests=file:data/semana_3/intereses.csv",
        "migration.data.annual-accounts=file:data/semana_3/cuentas_anuales.csv"
})
@DisplayName("The migration compared with the legacy results")
class TheLegacyEquivalenceIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Produces the legacy row counts for semana_3
     * 2. Produces the legacy daily transaction reports
     * 3. Produces the legacy account balances and interest rates
     * 4. Produces the legacy annual totals per account
     * 5. Summarizes the legacy daily reports per date
     */

    private static final String dataSet = "semana_3";

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrateSemana3() {
        clearMigrationData(jdbcTemplate);
        assertEquals(0, runAllMigrationsRunner.runJobs());
    }

    @Test
    @DisplayName("produces the legacy row counts for semana_3")
    void producesTheLegacyRowCountsForSemana3() {
        assertMatchesGolden("counts", GoldenSnapshot.counts);
    }

    @Test
    @DisplayName("produces the legacy daily transaction reports")
    void producesTheLegacyDailyTransactionReports() {
        assertMatchesGolden("daily_transaction_reports", GoldenSnapshot.dailyTransactionReports);
    }

    @Test
    @DisplayName("produces the legacy account balances and interest rates")
    void producesTheLegacyAccountBalancesAndInterestRates() {
        assertMatchesGolden("account_balances", GoldenSnapshot.accountBalances);
    }

    @Test
    @DisplayName("produces the legacy annual totals per account")
    void producesTheLegacyAnnualTotalsPerAccount() {
        assertMatchesGolden("annual_audit_reports", GoldenSnapshot.annualAuditReports);
    }

    @Test
    @DisplayName("summarizes the legacy daily reports per date")
    void summarizesTheLegacyDailyReportsPerDate() {
        assertMatchesGolden("daily_transaction_summaries", GoldenSnapshot.dailyTransactionSummaries);
    }

    private void assertMatchesGolden(String name, String query) {
        assertEquals(GoldenSnapshot.expected(dataSet, name), GoldenSnapshot.of(jdbcTemplate, query), name);
    }
}
