package cl.duoc.xyzbank.coredomain.transactions.unit;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.DateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The DateRange")
class DateRangeTest {

    /*
     * Cases:
     * 1. Creates with both bounds ordered validly
     * 2. Creates with only a from bound
     * 3. Creates with only a to bound
     * 4. Creates with no bounds
     * 5. Rejects a from date later than the to date
     */

    @Test
    @DisplayName("creates with both bounds ordered validly")
    void createsWithBothBoundsOrderedValidly() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        DateRange range = DateRange.create(Optional.of(from), Optional.of(to));

        assertEquals(Optional.of(from), range.getFrom());
        assertEquals(Optional.of(to), range.getTo());
    }

    @Test
    @DisplayName("creates with only a from bound")
    void createsWithOnlyAFromBound() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        DateRange range = DateRange.create(Optional.of(from), Optional.empty());

        assertEquals(Optional.of(from), range.getFrom());
        assertTrue(range.getTo().isEmpty());
    }

    @Test
    @DisplayName("creates with only a to bound")
    void createsWithOnlyAToBound() {
        LocalDate to = LocalDate.of(2026, 1, 31);

        DateRange range = DateRange.create(Optional.empty(), Optional.of(to));

        assertTrue(range.getFrom().isEmpty());
        assertEquals(Optional.of(to), range.getTo());
    }

    @Test
    @DisplayName("creates with no bounds")
    void createsWithNoBounds() {
        DateRange range = DateRange.create(Optional.empty(), Optional.empty());

        assertTrue(range.getFrom().isEmpty());
        assertTrue(range.getTo().isEmpty());
    }

    @Test
    @DisplayName("rejects a from date later than the to date")
    void rejectsAFromDateLaterThanTheToDate() {
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> DateRange.create(Optional.of(from), Optional.of(to)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
