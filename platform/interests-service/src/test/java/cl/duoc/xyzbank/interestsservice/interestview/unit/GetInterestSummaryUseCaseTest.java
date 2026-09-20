package cl.duoc.xyzbank.interestsservice.interestview.unit;

import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.interestview.application.usecases.GetInterestSummaryUseCase;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GetInterestSummaryUseCase")
class GetInterestSummaryUseCaseTest {

    /*
     * Cases:
     * 1. Returns interest summary from core-service
     * 2. Rejects null account ID
     * 3. Rejects blank account ID
     * 4. Rejects null year
     * 5. Rejects blank year
     * 6. Rejects non-numeric year
     */

    private InMemoryCoreServicePort coreServicePort;
    private GetInterestSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        coreServicePort = new InMemoryCoreServicePort();
        useCase = new GetInterestSummaryUseCase(coreServicePort);
    }

    @Nested
    @DisplayName("when fetching interest summary")
    class WhenFetchingInterestSummary {

        @Test
        @DisplayName("returns interest summary from core-service")
        void returnsInterestSummaryFromCoreService() {
            InterestSummaryResponse expected = new InterestSummaryResponse(
                    "account-123", 2025,
                    new BigDecimal("1000.00"), new BigDecimal("1035.00"),
                    new BigDecimal("0.035"), new BigDecimal("35.00"), "USD");
            coreServicePort.setResponse("account-123", "2025", expected);

            InterestSummaryResponse result = useCase.execute("account-123", "2025", "Bearer user-token");

            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("when validating account ID")
    class WhenValidatingAccountId {

        @Test
        @DisplayName("rejects null account ID")
        void rejectsNullAccountId() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute(null, "2025", "Bearer user-token"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
            assertEquals("Account ID is required", exception.getMessage());
        }

        @Test
        @DisplayName("rejects blank account ID")
        void rejectsBlankAccountId() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("   ", "2025", "Bearer user-token"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }
    }

    @Nested
    @DisplayName("when validating year")
    class WhenValidatingYear {

        @Test
        @DisplayName("rejects null year")
        void rejectsNullYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", null, "Bearer user-token"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
            assertEquals("Year is required", exception.getMessage());
        }

        @Test
        @DisplayName("rejects blank year")
        void rejectsBlankYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", "   ", "Bearer user-token"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }

        @Test
        @DisplayName("rejects non-numeric year")
        void rejectsNonNumericYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", "abc", "Bearer user-token"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
            assertEquals("Year must be a valid number", exception.getMessage());
        }
    }

    static class InMemoryCoreServicePort implements CoreServicePort {
        private String expectedAccountId;
        private String expectedYear;
        private InterestSummaryResponse response;

        void setResponse(String accountId, String year, InterestSummaryResponse response) {
            this.expectedAccountId = accountId;
            this.expectedYear = year;
            this.response = response;
        }

        @Override
        public InterestSummaryResponse fetchInterestSummary(String accountId, String year, String bearerToken) {
            if (expectedAccountId != null && expectedAccountId.equals(accountId)
                    && expectedYear != null && expectedYear.equals(year)) {
                return response;
            }
            throw DomainException.notFound("Interest summary not found");
        }
    }
}
