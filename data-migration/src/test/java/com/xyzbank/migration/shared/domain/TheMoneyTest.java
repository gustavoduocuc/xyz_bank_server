package com.xyzbank.migration.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheMoneyTest {

    /*
     * Cases:
     * 1. Creates money from amount
     * 2. Adds money amounts
     * 3. Multiplies money by rate factor
     * 4. Considers equal money with same amount
     * 5. Calculates absolute amount
     * 6. Considers amount above threshold
     */

    @Nested
    class TheMoney {

        @Test
        void createsMoneyFromAmount() {
            Money money = Money.create(1000);

            assertEquals(1000.0, money.amount());
        }

        @Test
        void addsMoneyAmounts() {
            Money total = Money.create(1000).add(Money.create(500));

            assertEquals(1500.0, total.amount());
        }

        @Test
        void multipliesMoneyByRateFactor() {
            Money balance = Money.create(5000).multiply(1.01);

            assertEquals(5050.0, balance.amount());
        }

        @Test
        void considersEqualMoneyWithSameAmount() {
            assertEquals(Money.create(100), Money.create(100));
        }

        @Test
        void calculatesAbsoluteAmount() {
            Money absolute = Money.create(-500).absolute();

            assertEquals(Money.create(500), absolute);
        }

        @Test
        void considersAmountAboveThreshold() {
            assertTrue(Money.create(3000).exceeds(2000));
            assertFalse(Money.create(1500).exceeds(2000));
        }
    }
}
