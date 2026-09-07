package com.xyzbank.migration.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TheBusinessDateTest {

    /*
     * Cases:
     * 1. Accepts dash formatted date (yyyy-MM-dd)
     * 2. Normalizes slash format to LocalDate (yyyy/MM/dd)
     * 3. Accepts day-first dash format (dd-MM-yyyy)
     * 4. Accepts day-first slash format (dd/MM/yyyy)
     * 5. Does not allow null date
     * 6. Does not allow blank date
     * 7. Does not allow invalid date format
     */

    @Nested
    class TheBusinessDate {

        @Test
        void acceptsDashFormattedDate() {
            BusinessDate date = BusinessDate.create("2024-01-01");

            assertEquals(LocalDate.of(2024, 1, 1), date.value());
        }

        @Test
        void normalizesSlashFormatToLocalDate() {
            BusinessDate date = BusinessDate.create("2024/01/15");

            assertEquals(LocalDate.of(2024, 1, 15), date.value());
            assertEquals("2024-01-15", date.asIso());
        }

        @Test
        void acceptsDayFirstDashFormat() {
            BusinessDate date = BusinessDate.create("08-03-2024");

            assertEquals(LocalDate.of(2024, 3, 8), date.value());
            assertEquals("2024-03-08", date.asIso());
        }

        @Test
        void acceptsDayFirstSlashFormat() {
            BusinessDate date = BusinessDate.create("24/03/2024");

            assertEquals(LocalDate.of(2024, 3, 24), date.value());
        }

        @Test
        void doesNotAllowNullDate() {
            assertThrows(DomainError.class, () -> BusinessDate.create(null));
        }

        @Test
        void doesNotAllowBlankDate() {
            assertThrows(DomainError.class, () -> BusinessDate.create("  "));
        }

        @Test
        void doesNotAllowInvalidDateFormat() {
            assertThrows(DomainError.class, () -> BusinessDate.create("2024.01.01"));
        }
    }
}
