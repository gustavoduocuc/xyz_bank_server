package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TheAnnualAccountCompilerTest {

    /*
     * Cases:
     * 1. Compiles one account from multiple movements
     * 2. Groups movements by account id
     * 3. Calculates net balance across deposits and withdrawals
     */

    @Nested
    class TheAnnualAccountCompiler {

        @Test
        void compilesOneAccountFromMultipleMovements() {
            List<AnnualMovement> movements = List.of(
                    AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso mensual"),
                    AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro parcial")
            );

            List<AnnualAccountSummary> summaries = AnnualAccountCompiler.compile(movements);

            assertEquals(1, summaries.size());
            AnnualAccountSummary summary = summaries.get(0);
            assertEquals("101", summary.accountId().value());
            assertEquals(Money.create(1000), summary.totalDeposits());
            assertEquals(Money.create(500), summary.totalWithdrawals());
            assertEquals(Money.create(500), summary.netBalance());
            assertEquals(2, summary.movementCount());
        }

        @Test
        void groupsMovementsByAccountId() {
            List<AnnualMovement> movements = List.of(
                    AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso mensual"),
                    AnnualMovement.create("102", "2024-05-22", "deposito", 1500, "Ingreso mensual")
            );

            List<AnnualAccountSummary> summaries = AnnualAccountCompiler.compile(movements);

            assertEquals(2, summaries.size());
        }

        @Test
        void calculatesNetBalanceAcrossDepositsAndWithdrawals() {
            List<AnnualMovement> movements = List.of(
                    AnnualMovement.create("104", "2024-09-05", "compra", -100, "Compra en tienda"),
                    AnnualMovement.create("104", "2024-10-01", "deposito", 2500, "Ingreso extra")
            );

            AnnualAccountSummary summary = AnnualAccountCompiler.compile(movements).get(0);

            assertEquals(Money.create(2500), summary.totalDeposits());
            assertEquals(Money.create(100), summary.totalWithdrawals());
            assertEquals(Money.create(2400), summary.netBalance());
        }
    }
}
