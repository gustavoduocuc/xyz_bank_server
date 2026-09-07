package cl.duoc.xyzbank.coreservice.accounts.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence.JpaAccountRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA account repository")
class JpaAccountRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Saves an account and finds it by id
     * 2. Finds only the accounts owned by a given customer
     * 3. Returns an empty list when the customer owns no accounts
     * 4. Rejects two accounts with the same account number
     */

    @Autowired
    private JpaAccountRepository accountRepository;

    @Test
    @DisplayName("saves an account and finds it by id")
    void savesAnAccountAndFindsItById() {
        Id id = Id.generate();
        Account account = Account.create(
                id, AccountNumber.create("1111111111"), Id.generate(),
                Money.create(new BigDecimal("100.00"), "USD"));

        accountRepository.save(account);
        Optional<Account> found = accountRepository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("1111111111", found.get().getAccountNumber().getValue());
    }

    @Test
    @DisplayName("finds only the accounts owned by a given customer")
    void findsOnlyTheAccountsOwnedByAGivenCustomer() {
        Id customerId = Id.generate();
        Id otherCustomerId = Id.generate();
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
                Id.generate(), sharedNumber, Id.generate(),
                Money.create(new BigDecimal("10.00"), "USD"));
        Account second = Account.create(
                Id.generate(), sharedNumber, Id.generate(),
                Money.create(new BigDecimal("20.00"), "USD"));
        accountRepository.save(first);

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.save(second));
    }
}
