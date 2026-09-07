package com.xyzbank.migration.monthlyinterests.domain;

import com.xyzbank.migration.shared.domain.DomainError;
import com.xyzbank.migration.shared.domain.Id;
import com.xyzbank.migration.shared.domain.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheAccountTest {

    /*
     * Cases:
     * 1. Creates valid savings account
     * 2. Does not allow zero balance
     * 3. Does not allow negative balance
     * 4. Does not allow age outside range
     * 5. Does not allow unknown account type
     * 6. Accepts accented loan type
     * 7. Does not allow empty name
     * 8. Considers senior account from age threshold
     */

    @Nested
    class TheAccount {

        @Test
        void createsValidSavingsAccount() {
            Account account = Account.create("101", "John Doe", 5000, 30, "ahorro");

            assertEquals(Id.create("101"), account.id());
            assertEquals("John Doe", account.name());
            assertEquals(Money.create(5000), account.balance());
            assertEquals(30, account.age());
            assertEquals(AccountType.SAVINGS, account.type());
        }

        @Test
        void doesNotAllowZeroBalance() {
            assertThrows(DomainError.class, () -> Account.create("104", "Alice Brown", 0, 45, "ahorro"));
        }

        @Test
        void doesNotAllowNegativeBalance() {
            assertThrows(DomainError.class, () -> Account.create("104", "Alice Brown", -10, 45, "ahorro"));
        }

        @Test
        void doesNotAllowAgeOutsideRange() {
            assertThrows(DomainError.class, () -> Account.create("101", "John Doe", 5000, 17, "ahorro"));
            assertThrows(DomainError.class, () -> Account.create("101", "John Doe", 5000, 101, "ahorro"));
        }

        @Test
        void doesNotAllowUnknownAccountType() {
            assertThrows(DomainError.class, () -> Account.create("101", "John Doe", 5000, 30, "corriente"));
            assertThrows(DomainError.class, () -> Account.create("101", "John Doe", 5000, 30, "-1"));
            assertThrows(DomainError.class, () -> Account.create("101", "John Doe", 5000, 30, "unknown"));
        }

        @Test
        void acceptsAccentedLoanType() {
            Account account = Account.create("102", "Jane Smith", 7000, 40, "préstamo");

            assertEquals(AccountType.LOAN, account.type());
        }

        @Test
        void doesNotAllowEmptyName() {
            assertThrows(DomainError.class, () -> Account.create("101", "  ", 5000, 30, "ahorro"));
        }

        @Test
        void considersSeniorAccountFromAgeThreshold() {
            Account junior = Account.create("101", "John Doe", 5000, 30, "ahorro");
            Account senior = Account.create("108", "Steve Rogers", 10000, 80, "ahorro");

            assertFalse(junior.isSenior());
            assertTrue(senior.isSenior());
        }
    }
}
