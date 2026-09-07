package cl.duoc.xyzbank.coredomain.interests.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The AnnualInterestSummary")
class AnnualInterestSummaryTest {

    /*
     * Cases:
     * 1. Creates with a valid account id, year, opening balance, closing balance, interest rate, and interest amount
     * 2. Rejects a null interest rate
     * 3. Rejects a negative interest rate
     */

    @Test
    @DisplayName("creates with a valid account id, year, opening balance, closing balance, interest rate, and interest amount")
    void createsWithAValidAccountIdYearOpeningBalanceClosingBalanceInterestRateAndInterestAmount() {
        Id id = Id.generate();
        Id accountId = Id.generate();
        Money opening = Money.create(new BigDecimal("1000.00"), "USD");
        Money closing = Money.create(new BigDecimal("1025.00"), "USD");
        Money interestAmount = Money.create(new BigDecimal("25.00"), "USD");
        BigDecimal interestRate = new BigDecimal("2.5000");

        AnnualInterestSummary summary = AnnualInterestSummary.create(
                id, accountId, 2025, opening, closing, interestRate, interestAmount);

        assertEquals(id, summary.getId());
        assertEquals(accountId, summary.getAccountId());
        assertEquals(2025, summary.getYear());
        assertEquals(opening, summary.getOpeningBalance());
        assertEquals(closing, summary.getClosingBalance());
        assertEquals(interestRate, summary.getInterestRate());
        assertEquals(interestAmount, summary.getInterestAmount());
    }

    @Test
    @DisplayName("rejects a null interest rate")
    void rejectsANullInterestRate() {
        Money money = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(DomainException.class, () -> AnnualInterestSummary.create(
                Id.generate(), Id.generate(), 2025, money, money, null, money));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a negative interest rate")
    void rejectsANegativeInterestRate() {
        Money money = Money.create(new BigDecimal("1000.00"), "USD");

        DomainException exception = assertThrows(DomainException.class, () -> AnnualInterestSummary.create(
                Id.generate(), Id.generate(), 2025, money, money, new BigDecimal("-1"), money));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
