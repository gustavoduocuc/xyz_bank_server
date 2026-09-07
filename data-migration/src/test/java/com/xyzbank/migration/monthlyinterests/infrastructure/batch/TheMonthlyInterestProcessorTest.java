package com.xyzbank.migration.monthlyinterests.infrastructure.batch;

import com.xyzbank.migration.monthlyinterests.domain.DuplicateAccountDetector;
import com.xyzbank.migration.monthlyinterests.domain.InterestApplied;
import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheMonthlyInterestProcessorTest {

    /*
     * Cases:
     * 1. Applies interest to valid account
     * 2. Does not allow zero balance
     * 3. Does not allow duplicate account id
     */

    @Nested
    class TheMonthlyInterestProcessor {

        private MonthlyInterestProcessor processor;

        @BeforeEach
        void setUp() {
            processor = new MonthlyInterestProcessor(new DuplicateAccountDetector());
        }

        @Test
        void appliesInterestToValidAccount() {
            InterestApplied applied = processor.process(line("101", "John Doe", 5000.0, 30, "ahorro"));

            assertEquals(0.01, applied.rate());
            assertEquals(Money.create(5050), applied.finalBalance());
        }

        @Test
        void doesNotAllowZeroBalance() {
            assertThrows(DomainError.class, () -> processor.process(line("104", "Alice Brown", 0.0, 45, "ahorro")));
        }

        @Test
        void doesNotAllowDuplicateAccountId() {
            processor.process(line("101", "John Doe", 5000.0, 30, "ahorro"));

            assertThrows(DomainError.class, () -> processor.process(line("101", "John Doe", 5000.0, 30, "ahorro")));
        }

        @Test
        void acceptsPaddedTextFields() {
            InterestApplied applied = processor.process(line(" 101 ", " John Doe ", 5000.0, 30, " ahorro "));

            assertEquals(0.01, applied.rate());
        }

        private InterestAccountLine line(String id, String name, Double balance, Integer age, String type) {
            return new InterestAccountLine(id, name, balance, age, type);
        }
    }
}
