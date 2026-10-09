package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.ProcessedInterestEventRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.interests.infrastructure.persistence.JdbcProcessedInterestEventRepository;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import cl.duoc.xyzbank.testsupport.KafkaTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("core-service's handling of interests.calculated records that fail")
class InterestCalculatedDeadLetterIT extends AbstractKafkaPostgresIT {

    /*
     * Cases (event-messaging spec, "A failing message is retried a bounded number of times and
     * then dead-lettered"), with the retry delays shortened to 50 ms / 100 ms / 200 ms:
     * 1. An invalid payload ends on interests.calculated.DLT with its key, payload and failure, and is counted in
     *    kafka.dlt.messages{topic="interests.calculated"}
     *    headers only after the retries, credits nothing, and a valid event for the same account
     *    published right after it is credited (its partition was not blocked)
     * 2. An event for an unknown account is a business rejection: InterestCreditRejected, and
     *    nothing on the dead-letter topic
     * 3. A first attempt that fails after the credit is committed is retried; the account is
     *    credited once and nothing is dead-lettered
     * 4. The same event arriving again with a recalculated amount (a repeated application after
     *    the balance rose) is acknowledged: credited once, one result row, nothing dead-lettered
     */

    private static final String CALCULATED = "interests.calculated";
    private static final String RESULTS = "interests.credit-results";
    private static final String DEAD_LETTER = "interests.calculated.DLT";
    private static final Duration TOTAL_BACKOFF = Duration.ofMillis(350);

    @DynamicPropertySource
    static void shortenTheRetryDelays(DynamicPropertyRegistry registry) {
        registry.add("interests.kafka.retry.max-retries", () -> "3");
        registry.add("interests.kafka.retry.initial-interval-ms", () -> "50");
        registry.add("interests.kafka.retry.multiplier", () -> "2.0");
    }

    @TestConfiguration
    static class FlakyRegistrationConfig {

        /** Fails the registration of the listed event ids once, after the credit was committed. */
        static final Set<String> FAIL_ONCE = ConcurrentHashMap.newKeySet();

        @Bean
        @Primary
        ProcessedInterestEventRepository flakyProcessedInterestEvents(JdbcProcessedInterestEventRepository real) {
            return new ProcessedInterestEventRepository() {
                @Override
                public Optional<String> findIdempotencyKey(String eventId) {
                    return real.findIdempotencyKey(eventId);
                }

                @Override
                public void register(String eventId, String idempotencyKey) {
                    if (FAIL_ONCE.remove(eventId)) {
                        throw new IllegalStateException("transient failure registering " + eventId);
                    }
                    real.register(eventId, idempotencyKey);
                }
            };
        }
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("dead-letters an invalid payload after the retries and keeps processing the partition")
    void deadLettersAnInvalidPayloadAfterTheRetriesAndKeepsProcessingThePartition() throws Exception {
        Account account = aSavedAccount("9080706101");
        String accountId = account.getId().getValue();
        String invalid = "this is not an InterestCalculated";
        long publishedAt = System.nanoTime();

        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId, invalid);
        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId,
                anInterestCalculated(accountId, "interest:" + accountId + ":2025", "35.00"));

        ConsumerRecord<String, String> dead =
                KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), DEAD_LETTER, accountId, 1).getFirst();
        Duration waited = Duration.ofNanos(System.nanoTime() - publishedAt);
        assertEquals(invalid, dead.value());
        assertEquals(CALCULATED, header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC));
        assertNotNull(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN));
        assertTrue(waited.compareTo(TOTAL_BACKOFF) >= 0, "dead-lettered after " + waited + ", before the retries ran");
        assertTrue(meterRegistry.get("kafka.dlt.messages").tag("topic", CALCULATED).counter().count() >= 1.0);

        JsonNode result = objectMapper.readTree(
                KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), RESULTS, accountId, 1).getFirst().value());
        assertEquals("InterestCreditApplied", result.get("eventType").asText());
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("reports a business rejection and writes nothing to the dead-letter topic")
    void reportsABusinessRejectionAndWritesNothingToTheDeadLetterTopic() throws Exception {
        String accountId = Id.generate().getValue();

        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId,
                anInterestCalculated(accountId, "interest:" + accountId + ":2025", "10.00"));

        JsonNode result = objectMapper.readTree(
                KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), RESULTS, accountId, 1).getFirst().value());
        assertEquals("InterestCreditRejected", result.get("eventType").asText());
        assertTrue(KafkaTestSupport.recordsWithKeyAfter(
                KAFKA.getBootstrapServers(), DEAD_LETTER, accountId, Duration.ofSeconds(2)).isEmpty());
    }

    @Test
    @DisplayName("retries a failure that happens after the credit and credits the account once")
    void retriesAFailureThatHappensAfterTheCreditAndCreditsTheAccountOnce() throws Exception {
        Account account = aSavedAccount("9080706103");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        FlakyRegistrationConfig.FAIL_ONCE.add(eventId);

        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId,
                anInterestCalculated(accountId, eventId, "35.00"));

        List<ConsumerRecord<String, String>> results =
                KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), RESULTS, accountId, 1);
        assertEquals("InterestCreditApplied", objectMapper.readTree(results.getFirst().value()).get("eventType").asText());
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(
                FlakyRegistrationConfig.FAIL_ONCE.isEmpty(), "the transient failure never happened"));
        assertTrue(KafkaTestSupport.recordsWithKeyAfter(
                KAFKA.getBootstrapServers(), DEAD_LETTER, accountId, Duration.ofSeconds(2)).isEmpty());
    }

    @Test
    @DisplayName("acknowledges a repeated application whose amount was recalculated, crediting once")
    void acknowledgesARepeatedApplicationWhoseAmountWasRecalculatedCreditingOnce() throws Exception {
        Account account = aSavedAccount("9080706104");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId,
                anInterestCalculated(accountId, eventId, "35.00"));
        KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), RESULTS, accountId, 1);

        KafkaTestSupport.send(KAFKA.getBootstrapServers(), CALCULATED, accountId,
                anInterestCalculated(accountId, eventId, "36.23"));

        assertTrue(KafkaTestSupport.recordsWithKeyAfter(
                KAFKA.getBootstrapServers(), DEAD_LETTER, accountId, Duration.ofSeconds(3)).isEmpty());
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
        assertEquals(1, KafkaTestSupport.recordsWithKeyAfter(
                KAFKA.getBootstrapServers(), RESULTS, accountId, Duration.ofSeconds(2)).size());
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String anInterestCalculated(String accountId, String eventId, String amount) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "InterestCalculated",
                  "schemaVersion": 1,
                  "accountId": "%s",
                  "period": 2025,
                  "amount": "%s",
                  "currency": "USD",
                  "interestRate": "0.0350",
                  "openingBalance": "1000.00",
                  "closingBalance": "1035.00",
                  "occurredAt": "2026-01-15"
                }
                """.formatted(eventId, accountId, amount);
    }

    private Account aSavedAccount(String accountNumber) {
        Id customerId = Id.generate();
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return accountRepository.findById(account.getId()).orElseThrow();
    }
}
