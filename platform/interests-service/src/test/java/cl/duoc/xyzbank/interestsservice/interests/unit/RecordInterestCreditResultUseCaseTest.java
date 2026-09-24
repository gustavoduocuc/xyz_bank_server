package cl.duoc.xyzbank.interestsservice.interests.unit;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCreditResult;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.RecordInterestCreditResultUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InMemoryInterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The record interest credit result use case")
class RecordInterestCreditResultUseCaseTest {

    /*
     * Cases:
     * 1. An applied result closes the calculation as applied
     * 2. A rejected result closes the calculation as rejected with its reason
     * 3. A second result does not change a calculation that is already closed
     */

    @Test
    @DisplayName("closes the calculation as applied when the result is applied")
    void closesTheCalculationAsAppliedWhenTheResultIsApplied() {
        InterestCalculationRepository calculations = new InMemoryInterestCalculationRepository();
        calculations.save(InterestCalculation.pending(
                "interest:account-1:2025", "account-1", 2025, new BigDecimal("35.00"), "USD"));
        RecordInterestCreditResultUseCase useCase = new RecordInterestCreditResultUseCase(calculations);

        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.APPLIED, null));

        InterestCalculation calculation = calculations.findByEventId("interest:account-1:2025").orElseThrow();
        assertEquals(InterestCalculationStatus.APPLIED, calculation.status());
        assertNull(calculation.reason());
    }

    @Test
    @DisplayName("closes the calculation as rejected with its reason when the result is rejected")
    void closesTheCalculationAsRejectedWithItsReasonWhenTheResultIsRejected() {
        InterestCalculationRepository calculations = pendingCalculation();
        RecordInterestCreditResultUseCase useCase = new RecordInterestCreditResultUseCase(calculations);

        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.REJECTED, "Account account-1 not found"));

        InterestCalculation calculation = calculations.findByEventId("interest:account-1:2025").orElseThrow();
        assertEquals(InterestCalculationStatus.REJECTED, calculation.status());
        assertEquals("Account account-1 not found", calculation.reason());
    }

    @Test
    @DisplayName("leaves a closed calculation unchanged when a second result arrives")
    void leavesAClosedCalculationUnchangedWhenASecondResultArrives() {
        InterestCalculationRepository calculations = pendingCalculation();
        RecordInterestCreditResultUseCase useCase = new RecordInterestCreditResultUseCase(calculations);
        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.APPLIED, null));

        useCase.execute(new InterestCreditResult(
                "interest:account-1:2025", InterestCalculationStatus.REJECTED, "Amount must be positive"));

        InterestCalculation calculation = calculations.findByEventId("interest:account-1:2025").orElseThrow();
        assertEquals(InterestCalculationStatus.APPLIED, calculation.status());
        assertNull(calculation.reason());
    }

    private InterestCalculationRepository pendingCalculation() {
        InterestCalculationRepository calculations = new InMemoryInterestCalculationRepository();
        calculations.save(InterestCalculation.pending(
                "interest:account-1:2025", "account-1", 2025, new BigDecimal("35.00"), "USD"));
        return calculations;
    }
}
