package com.xyzbank.migration.shared.infrastructure.batch;

import com.xyzbank.migration.shared.domain.DomainError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TheCsvFieldNormalizerTest {

    /*
     * Cases:
     * 1. Trims text and treats blank as null
     * 2. Parses amounts with spaces
     * 3. Parses comma decimals and mixed thousand separators
     * 4. Documents dot-only ambiguous amounts as BigDecimal decimals
     * 5. Rejects invalid amounts
     */

    @Nested
    class TheCsvFieldNormalizer {

        @Test
        void trimsTextAndTreatsBlankAsNull() {
            assertEquals("debito", CsvFieldNormalizer.text("  debito  "));
            assertNull(CsvFieldNormalizer.text("   "));
            assertNull(CsvFieldNormalizer.text(null));
        }

        @Test
        void parsesAmountsWithSpaces() {
            assertEquals(1500.50, CsvFieldNormalizer.amount(" 1500.50 "));
        }

        @Test
        void parsesCommaDecimalsAndThousandSeparators() {
            assertEquals(1500.50, CsvFieldNormalizer.amount("1500,50"));
            assertEquals(1500.50, CsvFieldNormalizer.amount("1.500,50"));
            assertEquals(1500.50, CsvFieldNormalizer.amount("1,500.50"));
        }

        @Test
        void treatsDotOnlyAmbiguousAmountAsDecimal() {
            assertEquals(1.50, CsvFieldNormalizer.amount("1.500"));
        }

        @Test
        void rejectsInvalidAmounts() {
            assertThrows(DomainError.class, () -> CsvFieldNormalizer.amount("abc"));
        }
    }
}
