package cl.duoc.xyzbank.interestsservice.interests.integration;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JDBC interest calculation repository")
class JdbcInterestCalculationRepositoryIT {

    /*
     * Cases (interests spec, "Interest calculations are stored in PostgreSQL"):
     * 1. A saved calculation is found by its event id with all its fields
     * 2. Saving the same event id again with a closed status updates the one row
     * 3. An event id that was never saved is not found
     */

    @Autowired
    private InterestCalculationRepository calculations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("finds a saved calculation by its event id with all its fields")
    void findsASavedCalculationByItsEventIdWithAllItsFields() {
        String eventId = "interest:" + UUID.randomUUID() + ":2025";
        calculations.save(InterestCalculation.pending(eventId, "account-1", 2025, new BigDecimal("35.00"), "USD"));

        InterestCalculation found = calculations.findByEventId(eventId).orElseThrow();

        assertEquals("account-1", found.accountId());
        assertEquals(2025, found.period());
        assertEquals(0, new BigDecimal("35.00").compareTo(found.amount()));
        assertEquals("USD", found.currency());
        assertEquals(InterestCalculationStatus.PENDING, found.status());
    }

    @Test
    @DisplayName("updates the one row when the same event id is saved again with a closed status")
    void updatesTheOneRowWhenTheSameEventIdIsSavedAgainWithAClosedStatus() {
        String eventId = "interest:" + UUID.randomUUID() + ":2025";
        InterestCalculation calculation =
                InterestCalculation.pending(eventId, "account-2", 2025, new BigDecimal("10.00"), "USD");
        calculations.save(calculation);

        calculation.reject("account closed");
        calculations.save(calculation);

        InterestCalculation found = calculations.findByEventId(eventId).orElseThrow();
        assertEquals(InterestCalculationStatus.REJECTED, found.status());
        assertEquals("account closed", found.reason());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM interests.interest_calculations WHERE event_id = ?", Integer.class, eventId));
    }

    @Test
    @DisplayName("finds nothing for an event id that was never saved")
    void findsNothingForAnEventIdThatWasNeverSaved() {
        assertTrue(calculations.findByEventId("interest:unknown:2025").isEmpty());
    }
}
