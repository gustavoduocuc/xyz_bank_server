package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Money;
import com.xyzbank.migration.shared.domain.SourceLine;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheAnnualMovementTest {

    /*
     * Cases:
     * 1. Creates valid deposit
     * 2. Creates valid deposit with accented type
     * 3. Creates valid withdrawal with negative amount
     * 4. Creates valid purchase with negative amount
     * 5. Does not allow zero deposit
     * 6. Does not allow unknown movement type
     * 7. Normalizes slash date
     * 8. Keeps the source line it was read from
     * 9. A deposit contributes its amount to deposits and net
     * 10. A negative withdrawal contributes its absolute value to withdrawals and its signed amount to net
     * 11. A positive withdrawal contributes its amount to withdrawals and net
     * 12. A purchase contributes its absolute value to withdrawals and its signed amount to net
     */

    @Nested
    class TheAnnualMovement {

        @Test
        void createsValidDeposit() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso mensual", SourceLine.of(1));

            assertEquals(MovementType.DEPOSIT, movement.type());
            assertEquals(Money.create(1000), movement.amount());
            assertTrue(movement.isDeposit());
        }

        @Test
        void createsValidDepositWithAccentedType() {
            AnnualMovement movement = AnnualMovement.create("102", "2024/09/30", "depósito", 2000, "Ingreso", SourceLine.of(1));

            assertEquals(MovementType.DEPOSIT, movement.type());
            assertTrue(movement.isDeposit());
        }

        @Test
        void createsValidWithdrawalWithNegativeAmount() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro parcial", SourceLine.of(1));

            assertEquals(MovementType.WITHDRAWAL, movement.type());
            assertEquals(Money.create(-500), movement.amount());
            assertTrue(movement.isOutgoing());
            assertEquals(Money.create(500), movement.absoluteAmount());
        }

        @Test
        void createsValidPurchaseWithNegativeAmount() {
            AnnualMovement movement = AnnualMovement.create("104", "2024-09-05", "compra", -100, "Compra en tienda", SourceLine.of(1));

            assertEquals(MovementType.PURCHASE, movement.type());
            assertTrue(movement.isOutgoing());
        }

        @Test
        void doesNotAllowZeroDeposit() {
            assertThrows(DomainError.class,
                    () -> AnnualMovement.create("107", "2024-12-25", "deposito", 0, "Ingreso navideño", SourceLine.of(1)));
        }

        @Test
        void doesNotAllowUnknownMovementType() {
            assertThrows(DomainError.class,
                    () -> AnnualMovement.create("101", "2024-01-01", "transfer", 1000, "x", SourceLine.of(1)));
        }

        @Test
        void normalizesSlashDate() {
            AnnualMovement movement = AnnualMovement.create("101", "2024/01/01", "deposito", 1000, "Ingreso", SourceLine.of(1));

            assertEquals("2024-01-01", movement.date().asIso());
        }

        @Test
        void keepsTheSourceLineItWasReadFrom() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso", SourceLine.of(9));

            assertEquals(SourceLine.of(9), movement.sourceLine());
        }

        @Test
        void aDepositContributesItsAmountToDepositsAndNet() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso", SourceLine.of(1));

            assertEquals(Money.create(1000), movement.depositContribution());
            assertEquals(Money.zero(), movement.withdrawalContribution());
            assertEquals(Money.create(1000), movement.netContribution());
        }

        @Test
        void aNegativeWithdrawalContributesItsAbsoluteValueToWithdrawalsAndItsSignedAmountToNet() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro", SourceLine.of(1));

            assertEquals(Money.zero(), movement.depositContribution());
            assertEquals(Money.create(500), movement.withdrawalContribution());
            assertEquals(Money.create(-500), movement.netContribution());
        }

        @Test
        void aPositiveWithdrawalContributesItsAmountToWithdrawalsAndNet() {
            AnnualMovement movement = AnnualMovement.create("110", "2024-07-24", "retiro", 1500, "", SourceLine.of(1));

            assertEquals(Money.zero(), movement.depositContribution());
            assertEquals(Money.create(1500), movement.withdrawalContribution());
            assertEquals(Money.create(1500), movement.netContribution());
        }

        @Test
        void aPurchaseContributesItsAbsoluteValueToWithdrawalsAndItsSignedAmountToNet() {
            AnnualMovement movement = AnnualMovement.create("104", "2024-09-05", "compra", -100, "Compra", SourceLine.of(1));

            assertEquals(Money.zero(), movement.depositContribution());
            assertEquals(Money.create(100), movement.withdrawalContribution());
            assertEquals(Money.create(-100), movement.netContribution());
        }
    }
}
