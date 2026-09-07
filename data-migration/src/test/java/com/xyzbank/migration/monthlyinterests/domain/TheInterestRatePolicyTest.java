package com.xyzbank.migration.monthlyinterests.domain;

import com.xyzbank.migration.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheInterestRatePolicyTest {

    /*
     * Cases:
     * 1. Applies 1% to savings under 65
     * 2. Applies 1.5% to savings 65 or older
     * 3. Applies 1.5% to loans
     * 4. Applies 0.8% to mortgages
     * 5. Calculates final balance with inferred rate
     */

    @Nested
    class TheInterestRatePolicy {

        @Test
        void appliesOnePercentToSavingsUnderSixtyFive() {
            Account account = Account.create("101", "John Doe", 5000, 30, "ahorro");

            assertEquals(0.01, InterestRatePolicy.rateFor(account));
        }

        @Test
        void appliesOneAndHalfPercentToSavingsSixtyFiveOrOlder() {
            Account account = Account.create("108", "Steve Rogers", 10000, 80, "ahorro");

            assertEquals(0.015, InterestRatePolicy.rateFor(account));
        }

        @Test
        void appliesOneAndHalfPercentToLoans() {
            Account account = Account.create("102", "Jane Smith", 8000, 25, "prestamo");

            assertEquals(0.015, InterestRatePolicy.rateFor(account));
        }

        @Test
        void appliesPointEightPercentToMortgages() {
            Account account = Account.create("105", "Charlie Green", 7000, 35, "hipoteca");

            assertEquals(0.008, InterestRatePolicy.rateFor(account));
        }

        @Test
        void calculatesFinalBalanceWithInferredRate() {
            Account account = Account.create("101", "John Doe", 5000, 30, "ahorro");

            InterestApplied applied = InterestRatePolicy.apply(account);

            assertEquals(0.01, applied.rate());
            assertEquals(Money.create(5050), applied.finalBalance());
        }
    }
}
