package com.xyzbank.migration.annualreports.infrastructure.batch;

import com.xyzbank.migration.annualreports.domain.AnnualMovement;
import com.xyzbank.migration.annualreports.domain.DuplicateMovementDetector;
import com.xyzbank.migration.annualreports.domain.MovementType;
import com.xyzbank.migration.shared.domain.DomainError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheAnnualMovementProcessorTest {

    /*
     * Cases:
     * 1. Accepts valid movement
     * 2. Accepts accented deposit type
     * 3. Does not allow zero deposit
     * 4. Does not allow duplicate movement
     * 5. Accepts padded text fields
     */

    @Nested
    class TheAnnualMovementProcessor {

        private AnnualMovementProcessor processor;

        @BeforeEach
        void setUp() {
            processor = new AnnualMovementProcessor(new DuplicateMovementDetector());
        }

        @Test
        void acceptsValidMovement() {
            AnnualMovement movement = processor.process(line("101", "2024-01-01", "deposito", 1000.0, "Ingreso mensual"));

            assertEquals(MovementType.DEPOSIT, movement.type());
        }

        @Test
        void acceptsAccentedDepositType() {
            AnnualMovement movement = processor.process(line("102", "2024/09/30", "depósito", 2000.0, "Ingreso"));

            assertEquals(MovementType.DEPOSIT, movement.type());
        }

        @Test
        void doesNotAllowZeroDeposit() {
            assertThrows(DomainError.class,
                    () -> processor.process(line("107", "2024-12-25", "deposito", 0.0, "Ingreso navideño")));
        }

        @Test
        void doesNotAllowDuplicateMovement() {
            processor.process(line("101", "2024-01-01", "deposito", 1000.0, "Ingreso mensual"));

            assertThrows(DomainError.class,
                    () -> processor.process(line("101", "2024-01-01", "deposito", 1000.0, "Ingreso mensual")));
        }

        @Test
        void acceptsPaddedTextFields() {
            AnnualMovement movement = processor.process(line(" 101 ", " 2024-01-01 ", " deposito ", 1000.0, " Ingreso mensual "));

            assertEquals(MovementType.DEPOSIT, movement.type());
            assertEquals("101", movement.accountIdValue());
        }

        private AnnualMovementLine line(String accountId, String date, String type, Double amount, String description) {
            return new AnnualMovementLine(accountId, date, type, amount, description);
        }
    }
}
