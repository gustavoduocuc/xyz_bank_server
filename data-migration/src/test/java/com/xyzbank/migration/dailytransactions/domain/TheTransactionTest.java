package com.xyzbank.migration.dailytransactions.domain;

import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheTransactionTest {

    /*
     * Cases:
     * 1. Creates valid debit transaction
     * 2. Creates valid credit transaction
     * 3. Normalizes slash date format
     * 4. Does not allow non-positive amount
     * 5. Does not allow unknown transaction type (invalid, desconocido)
     * 6. Accepts accented debit and credit types
     * 7. Does not allow empty id
     * 8. Does not allow empty date
     * 9. Shares business key for same date amount and type
     */

    @Nested
    class TheTransaction {

        @Test
        void createsValidDebitTransaction() {
            Transaction transaction = Transaction.create("1", "2024-01-01", 1000, "debito");

            assertEquals("1", transaction.id().value());
            assertEquals("2024-01-01", transaction.date().asIso());
            assertEquals(Money.create(1000), transaction.amount());
            assertEquals(TransactionType.DEBIT, transaction.type());
        }

        @Test
        void createsValidCreditTransaction() {
            Transaction transaction = Transaction.create("2", "2024-01-02", 1500, "credito");

            assertEquals(TransactionType.CREDIT, transaction.type());
        }

        @Test
        void normalizesSlashDateFormat() {
            Transaction transaction = Transaction.create("1", "2024/01/01", 1000, "debito");

            assertEquals("2024-01-01", transaction.date().asIso());
        }

        @Test
        void doesNotAllowNonPositiveAmount() {
            assertThrows(DomainError.class, () -> Transaction.create("3", "2024-01-03", -200, "debito"));
            assertThrows(DomainError.class, () -> Transaction.create("4", "2024-01-03", 0, "debito"));
        }

        @Test
        void doesNotAllowUnknownTransactionType() {
            DomainError transfer = assertThrows(
                    DomainError.class,
                    () -> Transaction.create("1", "2024-01-01", 1000, "transfer")
            );
            assertTrue(transfer.getMessage().contains("allowed: debito, credito"));

            assertThrows(DomainError.class, () -> Transaction.create("1", "2024-01-01", 1000, "invalid"));
            assertThrows(DomainError.class, () -> Transaction.create("1", "2024-01-01", 1000, "desconocido"));
        }

        @Test
        void acceptsAccentedDebitAndCreditTypes() {
            Transaction debit = Transaction.create("1", "2024-01-01", 1000, "débito");
            Transaction credit = Transaction.create("2", "2024-01-02", 1500, "crédito");

            assertEquals(TransactionType.DEBIT, debit.type());
            assertEquals(TransactionType.CREDIT, credit.type());
        }

        @Test
        void doesNotAllowEmptyId() {
            assertThrows(DomainError.class, () -> Transaction.create(" ", "2024-01-01", 1000, "debito"));
        }

        @Test
        void doesNotAllowEmptyDate() {
            assertThrows(DomainError.class, () -> Transaction.create("1", null, 1000, "debito"));
        }

        @Test
        void sharesBusinessKeyForSameDateAmountAndType() {
            Transaction first = Transaction.create("1", "2024-01-05", 700, "debito");
            Transaction second = Transaction.create("8", "2024-01-05", 700, "debito");

            assertEquals(first.businessKey(), second.businessKey());
        }
    }
}
