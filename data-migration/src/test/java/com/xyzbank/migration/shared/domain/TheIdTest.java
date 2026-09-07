package com.xyzbank.migration.shared.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheIdTest {

    /*
     * Cases:
     * 1. Creates id from non-empty value
     * 2. Does not allow empty id
     * 3. Does not allow null id
     * 4. Considers equal ids with same value
     */

    @Nested
    class TheId {

        @Test
        void createsIdFromNonEmptyValue() {
            Id id = Id.create("101");

            assertEquals("101", id.value());
        }

        @Test
        void doesNotAllowEmptyId() {
            DomainError error = assertThrows(DomainError.class, () -> Id.create("  "));

            assertEquals(DomainError.Type.VALIDATION, error.getType());
        }

        @Test
        void doesNotAllowNullId() {
            DomainError error = assertThrows(DomainError.class, () -> Id.create(null));

            assertEquals(DomainError.Type.VALIDATION, error.getType());
        }

        @Test
        void considersEqualIdsWithSameValue() {
            Id first = Id.create("101");
            Id second = Id.create("101");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }
    }
}
