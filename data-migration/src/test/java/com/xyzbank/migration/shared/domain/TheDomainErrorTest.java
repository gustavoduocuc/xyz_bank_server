package com.xyzbank.migration.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheDomainErrorTest {

    /*
     * Cases:
     * 1. Creates validation error with message and type
     * 2. Creates not-found error with message and type
     * 3. Creates other error with message and type
     */

    @Nested
    class TheDomainError {

        @Test
        void createsValidationErrorWithMessageAndType() {
            DomainError error = DomainError.validation("Amount must be positive");

            assertEquals("Amount must be positive", error.getMessage());
            assertEquals(DomainError.Type.VALIDATION, error.getType());
        }

        @Test
        void createsNotFoundErrorWithMessageAndType() {
            DomainError error = DomainError.notFound("Account 101 not found");

            assertEquals("Account 101 not found", error.getMessage());
            assertEquals(DomainError.Type.NOT_FOUND, error.getType());
        }

        @Test
        void createsOtherErrorWithMessageAndType() {
            DomainError error = DomainError.other("Unexpected domain failure");

            assertEquals("Unexpected domain failure", error.getMessage());
            assertEquals(DomainError.Type.OTHER, error.getType());
        }
    }
}
