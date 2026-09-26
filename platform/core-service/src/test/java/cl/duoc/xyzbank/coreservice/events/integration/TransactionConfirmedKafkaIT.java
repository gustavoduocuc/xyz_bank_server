package cl.duoc.xyzbank.coreservice.events.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence.JpaInterestCreditRepository;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawRequest;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.coreservice.withdrawals.application.usecases.WithdrawAccountUseCase;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("TransactionConfirmed outbox publication")
class TransactionConfirmedKafkaIT extends AbstractKafkaPostgresIT {

    /*
     * Cases:
     * 1. A successful withdrawal publishes exactly one TransactionConfirmed
     * 2. An idempotent retry does not publish a second event
     * 3. A rejected withdrawal (insufficient funds) publishes no event
     * 4. Events for the same account use accountId as the Kafka key
     * 5. A successful interest credit writes InterestCreditApplied and TransactionConfirmed together
     * 6. A failed surrounding transaction rolls back both outbox rows
     */

    @Autowired
    private WithdrawAccountUseCase withdrawAccountUseCase;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JpaInterestCreditRepository interestCreditRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("publishes exactly one TransactionConfirmed when a withdrawal succeeds")
    void publishesExactlyOneTransactionConfirmedWhenAWithdrawalSucceeds() throws Exception {
        Account account = aSavedAccount("9180706010");
        String accountId = account.getId().getValue();

        WithdrawalResponse response = withdrawAccountUseCase.execute(new WithdrawRequest(
                accountId, new BigDecimal("100.00"), "USD", "withdraw-ok-1"));

        JsonNode event = awaitTransactionConfirmed(accountId, response.transactionId());

        assertEquals("TransactionConfirmed", event.get("eventType").asText());
        assertEquals(response.transactionId(), event.get("eventId").asText());
        assertEquals(accountId, event.get("accountId").asText());
        assertEquals("WITHDRAWAL", event.get("type").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(new BigDecimal(event.get("amount").asText())));
        assertEquals("USD", event.get("currency").asText());
        assertEquals(1, countOutboxByEventId(response.transactionId()));
        assertTrue(publishedFlag(response.transactionId()));
    }

    @Test
    @DisplayName("does not publish a second TransactionConfirmed when the same Idempotency-Key is retried")
    void doesNotPublishASecondTransactionConfirmedWhenTheSameIdempotencyKeyIsRetried() throws Exception {
        Account account = aSavedAccount("9180706011");
        String accountId = account.getId().getValue();
        WithdrawRequest request = new WithdrawRequest(accountId, new BigDecimal("50.00"), "USD", "withdraw-retry-1");

        WithdrawalResponse first = withdrawAccountUseCase.execute(request);
        awaitTransactionConfirmed(accountId, first.transactionId());
        WithdrawalResponse second = withdrawAccountUseCase.execute(request);

        assertEquals(first.transactionId(), second.transactionId());
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(500)).untilAsserted(() ->
                assertEquals(1, countOutboxByEventId(first.transactionId())));
    }

    @Test
    @DisplayName("publishes no TransactionConfirmed when a withdrawal is rejected for insufficient funds")
    void publishesNoTransactionConfirmedWhenAWithdrawalIsRejectedForInsufficientFunds() {
        Account account = aSavedAccount("9180706012");
        String accountId = account.getId().getValue();
        int outboxBefore = countAllTransactionConfirmed();

        assertThrows(DomainException.class, () -> withdrawAccountUseCase.execute(new WithdrawRequest(
                accountId, new BigDecimal("5000.00"), "USD", "withdraw-reject-1")));

        assertEquals(outboxBefore, countAllTransactionConfirmed());
    }

    @Test
    @DisplayName("publishes TransactionConfirmed events for the same account with that accountId as the Kafka key")
    void publishesTransactionConfirmedEventsForTheSameAccountWithThatAccountIdAsTheKafkaKey() throws Exception {
        Account account = aSavedAccount("9180706013");
        String accountId = account.getId().getValue();

        WithdrawalResponse first = withdrawAccountUseCase.execute(new WithdrawRequest(
                accountId, new BigDecimal("10.00"), "USD", "withdraw-order-1"));
        WithdrawalResponse second = withdrawAccountUseCase.execute(new WithdrawRequest(
                accountId, new BigDecimal("20.00"), "USD", "withdraw-order-2"));

        List<ConsumerRecord<String, String>> records = awaitTransactionConfirmedRecords(
                accountId, List.of(first.transactionId(), second.transactionId()));

        assertEquals(2, records.size());
        assertTrue(records.stream().allMatch(record -> accountId.equals(record.key())));
        List<String> eventIds = records.stream()
                .map(record -> {
                    try {
                        return objectMapper.readTree(record.value()).get("eventId").asText();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
        assertTrue(eventIds.contains(first.transactionId()));
        assertTrue(eventIds.contains(second.transactionId()));
    }

    @Test
    @DisplayName("writes TransactionConfirmed and InterestCreditApplied together for an interest credit")
    void writesTransactionConfirmedAndInterestCreditAppliedTogetherForAnInterestCredit() {
        Account account = aSavedAccount("9180706015");
        account.credit(Money.create(new BigDecimal("35.00"), "USD"));
        String interestEventId = "interest:" + account.getId().getValue() + ":2025";
        Transaction transaction = Transaction.create(
                Id.generate(),
                account.getId(),
                TransactionType.CREDIT,
                Money.create(new BigDecimal("35.00"), "USD"),
                LocalDate.of(2026, 1, 15),
                null,
                Optional.of(interestEventId));
        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(),
                account.getId(),
                2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1035.00"), "USD"),
                new BigDecimal("0.0350"),
                Money.create(new BigDecimal("35.00"), "USD"));

        interestCreditRepository.persistInterestCredit(account, transaction, summary);

        assertEquals(1, countOutboxByEventId(interestEventId));
        assertEquals(1, countOutboxByEventId(transaction.getId().getValue()));
        assertEquals(
                "InterestCreditApplied",
                jdbcTemplate.queryForObject(
                        "SELECT event_type FROM outbox_events WHERE event_id = ?",
                        String.class,
                        interestEventId));
        assertEquals(
                "TransactionConfirmed",
                jdbcTemplate.queryForObject(
                        "SELECT event_type FROM outbox_events WHERE event_id = ?",
                        String.class,
                        transaction.getId().getValue()));
        assertEquals(
                "INTEREST_CREDIT",
                jdbcTemplate.queryForObject(
                        "SELECT movement_type FROM outbox_events WHERE event_id = ?",
                        String.class,
                        transaction.getId().getValue()));
    }

    @Test
    @DisplayName("rolls back TransactionConfirmed with InterestCreditApplied when the surrounding transaction fails")
    void rollsBackTransactionConfirmedWithInterestCreditAppliedWhenTheSurroundingTransactionFails() {
        Account account = aSavedAccount("9180706014");
        account.credit(Money.create(new BigDecimal("35.00"), "USD"));
        String interestEventId = "interest:" + account.getId().getValue() + ":2025";
        Transaction transaction = Transaction.create(
                Id.generate(),
                account.getId(),
                TransactionType.CREDIT,
                Money.create(new BigDecimal("35.00"), "USD"),
                LocalDate.of(2026, 1, 15),
                null,
                Optional.of(interestEventId));
        AnnualInterestSummary summary = AnnualInterestSummary.create(
                Id.generate(),
                account.getId(),
                2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1035.00"), "USD"),
                new BigDecimal("0.0350"),
                Money.create(new BigDecimal("35.00"), "USD"));

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            interestCreditRepository.persistInterestCredit(account, transaction, summary);
            throw new IllegalStateException("simulated failure after dual outbox write");
        }));

        assertEquals(0, countOutboxByEventId(interestEventId));
        assertEquals(0, countOutboxByEventId(transaction.getId().getValue()));
    }

    private JsonNode awaitTransactionConfirmed(String accountId, String eventId) throws Exception {
        List<ConsumerRecord<String, String>> records = awaitTransactionConfirmedRecords(accountId, List.of(eventId));
        return objectMapper.readTree(records.getFirst().value());
    }

    private List<ConsumerRecord<String, String>> awaitTransactionConfirmedRecords(
            String accountId, List<String> eventIds) throws Exception {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = confirmedConsumer(accountId)) {
            consumer.subscribe(List.of("transactions.confirmed"));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (!accountId.equals(record.key())) {
                        return;
                    }
                    try {
                        String eventId = objectMapper.readTree(record.value()).get("eventId").asText();
                        if (eventIds.contains(eventId)
                                && matching.stream().noneMatch(existing -> sameEvent(existing, eventId))) {
                            matching.add(record);
                        }
                    } catch (Exception ignored) {
                    }
                });
                return matching.size() >= eventIds.size();
            });
        }
        return matching;
    }

    private boolean sameEvent(ConsumerRecord<String, String> existing, String eventId) {
        try {
            return eventId.equals(objectMapper.readTree(existing.value()).get("eventId").asText());
        } catch (Exception exception) {
            return false;
        }
    }

    private KafkaConsumer<String, String> confirmedConsumer(String accountId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "transaction-confirmed-it-" + accountId + "-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private boolean publishedFlag(String eventId) {
        Boolean published = jdbcTemplate.queryForObject(
                "SELECT published FROM outbox_events WHERE event_id = ?",
                Boolean.class,
                eventId);
        return Boolean.TRUE.equals(published);
    }

    private int countOutboxByEventId(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_id = ?",
                Integer.class,
                eventId);
        return count == null ? 0 : count;
    }

    private int countAllTransactionConfirmed() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'TransactionConfirmed'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private Account aSavedAccount(String accountNumber) {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Events Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return accountRepository.findById(account.getId()).orElseThrow();
    }
}
