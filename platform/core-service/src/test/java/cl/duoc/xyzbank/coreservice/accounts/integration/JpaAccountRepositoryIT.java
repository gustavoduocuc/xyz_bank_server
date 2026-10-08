package cl.duoc.xyzbank.coreservice.accounts.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountStatus;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence.JpaAccountRepository;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA account repository")
class JpaAccountRepositoryIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Saves an account and finds it by id
     * 2. Finds only the accounts owned by a given customer
     * 3. Returns an empty list when the customer owns no accounts
     * 4. Rejects two accounts with the same account number
     * 5. Rejects a save based on a stale version (optimistic lock conflict)
     * 6. Round-trips the status, alias, own daily limit and last command key
     * 7. Returns the first account when the opening key is already taken
     * 8. Reads an account saved without lifecycle data as ACTIVE with no own limit
     */

    @Autowired
    private JpaAccountRepository accountRepository;

    @Test
    @DisplayName("saves an account and finds it by id")
    void savesAnAccountAndFindsItById() {
        Id id = Id.generate();
        Account account = Account.create(
                id, AccountNumber.create("1111111111"), aSavedCustomer(),
                Money.create(new BigDecimal("100.00"), "USD"));

        accountRepository.save(account);
        Optional<Account> found = accountRepository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("1111111111", found.get().getAccountNumber().getValue());
    }

    @Test
    @DisplayName("finds only the accounts owned by a given customer")
    void findsOnlyTheAccountsOwnedByAGivenCustomer() {
        Id customerId = aSavedCustomer();
        Id otherCustomerId = aSavedCustomer();
        Account ownedAccount = Account.create(
                Id.generate(), AccountNumber.create("2222222222"), customerId,
                Money.create(new BigDecimal("50.00"), "USD"));
        Account otherAccount = Account.create(
                Id.generate(), AccountNumber.create("3333333333"), otherCustomerId,
                Money.create(new BigDecimal("75.00"), "USD"));
        accountRepository.save(ownedAccount);
        accountRepository.save(otherAccount);

        List<Account> accounts = accountRepository.findByCustomerId(customerId);

        assertEquals(1, accounts.size());
        assertEquals("2222222222", accounts.get(0).getAccountNumber().getValue());
    }

    @Test
    @DisplayName("returns an empty list when the customer owns no accounts")
    void returnsAnEmptyListWhenTheCustomerOwnsNoAccounts() {
        List<Account> accounts = accountRepository.findByCustomerId(Id.generate());

        assertTrue(accounts.isEmpty());
    }

    @Test
    @DisplayName("rejects two accounts with the same account number")
    void rejectsTwoAccountsWithTheSameAccountNumber() {
        AccountNumber sharedNumber = AccountNumber.create("4444444444");
        Account first = Account.create(
                Id.generate(), sharedNumber, aSavedCustomer(),
                Money.create(new BigDecimal("10.00"), "USD"));
        Account second = Account.create(
                Id.generate(), sharedNumber, aSavedCustomer(),
                Money.create(new BigDecimal("20.00"), "USD"));
        accountRepository.save(first);

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.save(second));
    }

    @Test
    @DisplayName("rejects a save based on a stale version")
    void rejectsASaveBasedOnAStaleVersion() {
        Id id = Id.generate();
        Account original = Account.create(
                id, AccountNumber.create("5555555555"), aSavedCustomer(),
                Money.create(new BigDecimal("500.00"), "USD"));
        accountRepository.save(original);
        Account firstCopy = accountRepository.findById(id).orElseThrow();
        Account secondCopy = accountRepository.findById(id).orElseThrow();

        firstCopy.withdraw(Money.create(new BigDecimal("100.00"), "USD"), LocalDate.of(2026, 1, 1),
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(firstCopy);

        secondCopy.withdraw(Money.create(new BigDecimal("50.00"), "USD"), LocalDate.of(2026, 1, 1),
                Money.create(new BigDecimal("1000.00"), "USD"));
        DomainException exception = assertThrows(DomainException.class, () -> accountRepository.save(secondCopy));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    @Test
    @DisplayName("round-trips the status, alias, own daily limit and last command key")
    void roundTripsTheStatusAliasOwnDailyLimitAndLastCommandKey() {
        Id id = Id.generate();
        Account opened = accountRepository.saveOpened(
                Account.open(id, uniqueNumber(), aSavedCustomer(), "USD", "Ahorro"), "open-" + id.getValue());
        opened.updateDetails("Viajes", Money.create(new BigDecimal("800.00"), "USD"), 0, "upd-1");
        accountRepository.save(opened);
        Account updated = accountRepository.findById(id).orElseThrow();
        updated.close(1, "close-1");
        accountRepository.save(updated);

        Account found = accountRepository.findById(id).orElseThrow();

        assertEquals(AccountStatus.CLOSED, found.getStatus());
        assertEquals(Optional.of("Viajes"), found.getAlias());
        assertEquals(Optional.of(Money.create(new BigDecimal("800.00"), "USD")), found.getDailyWithdrawalLimit());
        assertTrue(found.isRetryOfLastCommand("close-1"));
        assertEquals(2, found.getVersion());
        assertEquals(id, accountRepository.findByOpeningIdempotencyKey("open-" + id.getValue()).orElseThrow().getId());
    }

    @Test
    @DisplayName("returns the first account when the opening key is already taken")
    void returnsTheFirstAccountWhenTheOpeningKeyIsAlreadyTaken() {
        String key = "open-" + Id.generate().getValue();
        Account first = accountRepository.saveOpened(
                Account.open(Id.generate(), uniqueNumber(), aSavedCustomer(), "USD", null), key);

        Account second = accountRepository.saveOpened(
                Account.open(Id.generate(), uniqueNumber(), aSavedCustomer(), "USD", null), key);

        assertEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("reads an account saved without lifecycle data as ACTIVE with no own limit")
    void readsAnAccountSavedWithoutLifecycleDataAsActiveWithNoOwnLimit() {
        Id id = Id.generate();
        accountRepository.save(Account.create(
                id, uniqueNumber(), aSavedCustomer(), Money.create(new BigDecimal("10.00"), "USD")));

        Account found = accountRepository.findById(id).orElseThrow();

        assertEquals(AccountStatus.ACTIVE, found.getStatus());
        assertTrue(found.getDailyWithdrawalLimit().isEmpty());
    }

    private static AccountNumber uniqueNumber() {
        return AccountNumber.create(String.format("%010d", Math.floorMod(System.nanoTime(), 10_000_000_000L)));
    }

    private Id aSavedCustomer() {
        Id customerId = Id.generate();
        return customerId;
    }
}
