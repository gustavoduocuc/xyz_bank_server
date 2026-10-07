package com.xyzbank.migration.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheSourceLineTest {

    /*
     * Cases:
     * 1. Creates a source line from a positive number
     * 2. Does not allow line zero
     * 3. Does not allow a negative line
     */

    @Nested
    class TheSourceLine {

        @Test
        void createsASourceLineFromAPositiveNumber() {
            SourceLine line = SourceLine.of(12);

            assertEquals(12, line.number());
        }

        @Test
        void doesNotAllowLineZero() {
            DomainError error = assertThrows(DomainError.class, () -> SourceLine.of(0));

            assertEquals(DomainError.Type.VALIDATION, error.getType());
        }

        @Test
        void doesNotAllowANegativeLine() {
            DomainError error = assertThrows(DomainError.class, () -> SourceLine.of(-3));

            assertEquals(DomainError.Type.VALIDATION, error.getType());
        }
    }
}
