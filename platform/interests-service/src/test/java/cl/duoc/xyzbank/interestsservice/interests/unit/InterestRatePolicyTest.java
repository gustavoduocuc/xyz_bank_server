package cl.duoc.xyzbank.interestsservice.interests.unit;

import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The InterestRatePolicy")
class InterestRatePolicyTest {

    /*
     * Cases:
     * 1. Calculates interest as opening balance times configured rate
     * 2. Rounds interest amount to 2 decimal places half-up
     * 3. Exposes the configured rate
     */

    @Nested
    @DisplayName("when calculating annual interest")
    class WhenCalculatingAnnualInterest {

        @Test
        @DisplayName("calculates interest as opening balance times configured rate")
        void calculatesInterestAsOpeningBalanceTimesConfiguredRate() {
            InterestRatePolicy policy = new InterestRatePolicy(new BigDecimal("0.035"));

            BigDecimal interestAmount = policy.calculateInterestAmount(new BigDecimal("1000.00"));

            assertEquals(new BigDecimal("35.00"), interestAmount);
        }

        @Test
        @DisplayName("rounds interest amount to two decimal places half up")
        void roundsInterestAmountToTwoDecimalPlacesHalfUp() {
            InterestRatePolicy policy = new InterestRatePolicy(new BigDecimal("0.035"));

            BigDecimal interestAmount = policy.calculateInterestAmount(new BigDecimal("100.00"));

            assertEquals(new BigDecimal("3.50"), interestAmount);
        }

        @Test
        @DisplayName("exposes the configured annual rate")
        void exposesTheConfiguredAnnualRate() {
            InterestRatePolicy policy = new InterestRatePolicy(new BigDecimal("0.035"));

            assertEquals(new BigDecimal("0.035"), policy.annualRate());
        }
    }
}
