package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.unit.InMemoryInterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.interests.application.dto.AnnualInterestSummaryResponse;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The GetAnnualInterestSummary use case")
class GetAnnualInterestSummaryUseCaseTest {

    /*
     * Cases:
     * 1. Returns an existing summary's response
     * 2. Throws not found when no summary exists for that account and year
     * 3. Throws validation for a missing year
     * 4. Throws validation for a non-numeric year
     * 5. Throws validation for a malformed account id
     */

    @Test
    @DisplayName("returns an existing summary's response")
    void returnsAnExistingSummarysResponse() {
        Id accountId = Id.generate();
        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(), accountId, 2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1025.00"), "USD"),
                new BigDecimal("2.5000"),
                Money.create(new BigDecimal("25.00"), "USD"));
        InMemoryInterestSummaryRepository repository = new InMemoryInterestSummaryRepository();
        repository.save(summary);
        GetAnnualInterestSummaryUseCase useCase = new GetAnnualInterestSummaryUseCase(repository);

        AnnualInterestSummaryResponse response = useCase.execute(accountId.getValue(), "2025");

        assertEquals(accountId.getValue(), response.accountId());
        assertEquals(2025, response.year());
        assertEquals(new BigDecimal("1000.00"), response.openingBalance());
        assertEquals(new BigDecimal("1025.00"), response.closingBalance());
        assertEquals(new BigDecimal("2.5000"), response.interestRate());
        assertEquals(new BigDecimal("25.00"), response.interestAmount());
        assertEquals("USD", response.currency());
    }

    @Test
    @DisplayName("throws not found when no summary exists for that account and year")
    void throwsNotFoundWhenNoSummaryExistsForThatAccountAndYear() {
        GetAnnualInterestSummaryUseCase useCase =
                new GetAnnualInterestSummaryUseCase(new InMemoryInterestSummaryRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue(), "2025"));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a missing year")
    void throwsValidationForAMissingYear() {
        GetAnnualInterestSummaryUseCase useCase =
                new GetAnnualInterestSummaryUseCase(new InMemoryInterestSummaryRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue(), null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a non-numeric year")
    void throwsValidationForANonNumericYear() {
        GetAnnualInterestSummaryUseCase useCase =
                new GetAnnualInterestSummaryUseCase(new InMemoryInterestSummaryRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue(), "abcd"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a malformed account id")
    void throwsValidationForAMalformedAccountId() {
        GetAnnualInterestSummaryUseCase useCase =
                new GetAnnualInterestSummaryUseCase(new InMemoryInterestSummaryRepository());

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("   ", "2025"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
