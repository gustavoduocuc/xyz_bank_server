package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence.JpaInterestSummaryRepository;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The JPA interest summary repository")
class JpaInterestSummaryRepositoryIT extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. Saves a summary and finds it by account id and year
     * 2. Returns empty for an unknown account and year pair
     */

    @Autowired
    private JpaInterestSummaryRepository interestSummaryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("saves a summary and finds it by account id and year")
    void savesASummaryAndFindsItByAccountIdAndYear() {
        Id accountId = aSavedAccountId();
        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(), accountId, 2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1025.00"), "USD"),
                new BigDecimal("2.5000"),
                Money.create(new BigDecimal("25.00"), "USD"));

        interestSummaryRepository.save(summary);
        Optional<AnnualInterestSummary> found = interestSummaryRepository.findByAccountIdAndYear(accountId, 2025);

        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("25.00"), found.get().getInterestAmount().getAmount());
    }

    @Test
    @DisplayName("returns empty for an unknown account and year pair")
    void returnsEmptyForAnUnknownAccountAndYearPair() {
        Optional<AnnualInterestSummary> found =
                interestSummaryRepository.findByAccountIdAndYear(Id.generate(), 2020);

        assertTrue(found.isEmpty());
    }

    private Id aSavedAccountId() {
        Id customerId = Id.generate();
        Account account = Account.create(
                Id.generate(), AccountNumber.create(aUniqueAccountNumber()), customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return account.getId();
    }

    private static String aUniqueAccountNumber() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }
}
