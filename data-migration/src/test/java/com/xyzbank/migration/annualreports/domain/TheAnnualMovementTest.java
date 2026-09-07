package com.xyzbank.migration.annualreports.domain;

import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Money;
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
     */

    @Nested
    class TheAnnualMovement {

        @Test
        void createsValidDeposit() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-01-01", "deposito", 1000, "Ingreso mensual");

            assertEquals(MovementType.DEPOSIT, movement.type());
            assertEquals(Money.create(1000), movement.amount());
            assertTrue(movement.isDeposit());
        }

        @Test
        void createsValidDepositWithAccentedType() {
            AnnualMovement movement = AnnualMovement.create("102", "2024/09/30", "depósito", 2000, "Ingreso");

            assertEquals(MovementType.DEPOSIT, movement.type());
            assertTrue(movement.isDeposit());
        }

        @Test
        void createsValidWithdrawalWithNegativeAmount() {
            AnnualMovement movement = AnnualMovement.create("101", "2024-03-15", "retiro", -500, "Retiro parcial");

            assertEquals(MovementType.WITHDRAWAL, movement.type());
            assertEquals(Money.create(-500), movement.amount());
            assertTrue(movement.isOutgoing());
            assertEquals(Money.create(500), movement.absoluteAmount());
        }

        @Test
        void createsValidPurchaseWithNegativeAmount() {
            AnnualMovement movement = AnnualMovement.create("104", "2024-09-05", "compra", -100, "Compra en tienda");

            assertEquals(MovementType.PURCHASE, movement.type());
            assertTrue(movement.isOutgoing());
        }

        @Test
        void doesNotAllowZeroDeposit() {
            assertThrows(DomainError.class,
                    () -> AnnualMovement.create("107", "2024-12-25", "deposito", 0, "Ingreso navideño"));
        }

        @Test
        void doesNotAllowUnknownMovementType() {
            assertThrows(DomainError.class,
                    () -> AnnualMovement.create("101", "2024-01-01", "transfer", 1000, "x"));
        }

        @Test
        void normalizesSlashDate() {
            AnnualMovement movement = AnnualMovement.create("101", "2024/01/01", "deposito", 1000, "Ingreso");

            assertEquals("2024-01-01", movement.date().asIso());
        }
    }
}
