package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence.JpaInterestCreditRepository;
import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DisplayName("The JPA interest credit repository")
class JpaInterestCreditRepositoryIT extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. A successful credit writes the outbox event and the processed interest event
     * 2. A failure after the balance is saved rolls the credit back and leaves no outbox row
     */

    @Autowired
    private JpaInterestCreditRepository interestCreditRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("writes an outbox event and a processed interest event when the credit succeeds")
    void writesAnOutboxEventAndAProcessedInterestEventWhenTheCreditSucceeds() {
        Account account = aSavedAccount("9080706011");
        String eventId = "interest:" + account.getId().getValue() + ":2025";
        account.credit(Money.create(new BigDecimal("35.00"), "USD"));

        interestCreditRepository.persistInterestCredit(
                account, aCredit(account, eventId), aSummary(account));

        assertEquals(1, count("outbox_events", eventId));
        assertEquals(1, count("processed_interest_events", eventId));
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("rolls the credit back and leaves no outbox row when the transaction fails after the balance is saved")
    void rollsTheCreditBackAndLeavesNoOutboxRowWhenTheTransactionFailsAfterTheBalanceIsSaved() {
        Account account = aSavedAccount("9080706012");
        String eventId = "interest:" + account.getId().getValue() + ":2025";
        account.credit(Money.create(new BigDecimal("35.00"), "USD"));

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            interestCreditRepository.persistInterestCredit(
                    account, aCredit(account, eventId), aSummary(account));
            throw new IllegalStateException("simulated failure after saving the balance");
        }));

        assertEquals(0, count("outbox_events", eventId));
        assertEquals(0, count("processed_interest_events", eventId));
        assertEquals(
                new BigDecimal("1000.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    private int count(String table, String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?",
                Integer.class,
                eventId);
        return count == null ? 0 : count;
    }

    private Account aSavedAccount(String accountNumber) {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Interest Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return accountRepository.findById(account.getId()).orElseThrow();
    }

    private Transaction aCredit(Account account, String eventId) {
        return Transaction.create(
                Id.generate(),
                account.getId(),
                TransactionType.CREDIT,
                Money.create(new BigDecimal("35.00"), "USD"),
                LocalDate.of(2026, 1, 15),
                null,
                Optional.of(eventId));
    }

    private AnnualInterestSummary aSummary(Account account) {
        return AnnualInterestSummary.create(
                Id.generate(),
                account.getId(),
                2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1035.00"), "USD"),
                new BigDecimal("0.0350"),
                Money.create(new BigDecimal("35.00"), "USD"));
    }
}
