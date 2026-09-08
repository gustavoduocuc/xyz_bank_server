package cl.duoc.xyzbank.bffweb.interestview.unit;

import cl.duoc.xyzbank.bffweb.interestview.application.dto.InterestViewResponse;
import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.interestview.application.usecases.InterestViewUseCase;
import cl.duoc.xyzbank.bffweb.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("The Interest View use case")
class InterestViewUseCaseTest {

    /*
     * Cases:
     * 1. Successful summary
     * 2. Missing summary is not-found
     * 3. Missing year is a validation failure
     */

    @Test
    @DisplayName("returns the annual interest summary for an account and year")
    void returnsTheAnnualInterestSummary() {
        InterestViewResponse summary = new InterestViewResponse(
                "account-1",
                2026,
                new BigDecimal("1000.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("0.05"),
                new BigDecimal("50.00"),
                "USD");
        InterestViewUseCase useCase = new InterestViewUseCase((accountId, year) -> summary);

        InterestViewResponse response = useCase.execute("account-1", "2026");

        assertEquals(summary, response);
    }

    @Test
    @DisplayName("propagates not-found when no summary exists")
    void propagatesNotFoundWhenNoSummaryExists() {
        InterestPort port = (accountId, year) -> {
            throw new CoreServiceCallException(404, "No interest summary");
        };
        InterestViewUseCase useCase = new InterestViewUseCase(port);

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class, () -> useCase.execute("account-1", "2026"));

        assertEquals(404, exception.getStatus());
    }

    @Test
    @DisplayName("rejects a missing year")
    void rejectsAMissingYear() {
        InterestViewUseCase useCase = new InterestViewUseCase((accountId, year) -> fail("port should not be called"));

        assertThrows(RequestRejectedException.class, () -> useCase.execute("account-1", null));
    }
}
