package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.interests.domain.entities.AnnualInterestSummary;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The interest credit listener and relay")
class InterestCreditKafkaIT extends AbstractKafkaPostgresIT {

    /*
     * Cases:
     * 1. An InterestCalculated event credits the account, stores the result in the outbox,
     *    and the relay publishes InterestCreditApplied on interests.credit-results
     * 2. An unknown account publishes InterestCreditRejected and does not credit a balance
     * 3. A duplicate InterestCalculated event credits the balance only once
     * 4. A year already credited via HTTP publishes InterestCreditRejected
     * 5. An event published while the listener is stopped is credited after the listener restarts
     */

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InterestSummaryRepository interestSummaryRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Test
    @DisplayName("credits the account and publishes InterestCreditApplied when an InterestCalculated event arrives")
    void creditsTheAccountAndPublishesInterestCreditAppliedWhenAnInterestCalculatedEventArrives() throws Exception {
        Account account = aSavedAccount("9080706013");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";

        JsonNode result = publishedResult(accountId, anInterestCalculated(accountId, eventId, "35.00"));

        assertEquals("InterestCreditApplied", result.get("eventType").asText());
        assertEquals(eventId, result.get("eventId").asText());
        assertEquals(accountId, result.get("accountId").asText());
        assertEquals(0, new BigDecimal("35.00").compareTo(new BigDecimal(result.get("amount").asText())));
        assertEquals("USD", result.get("currency").asText());
        assertEquals(true, publishedFlag(eventId));
        assertEquals(
                new BigDecimal("1035.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("publishes InterestCreditRejected when the account does not exist")
    void publishesInterestCreditRejectedWhenTheAccountDoesNotExist() throws Exception {
        String accountId = Id.generate().getValue();
        String eventId = "interest:" + accountId + ":2025";

        JsonNode result = publishedResult(accountId, anInterestCalculated(accountId, eventId, "10.00"));

        assertEquals("InterestCreditRejected", result.get("eventType").asText());
        assertEquals(eventId, result.get("eventId").asText());
        assertEquals("Account " + accountId + " not found", result.get("reason").asText());
        assertEquals(true, publishedFlag(eventId));
        assertEquals(0, countAccounts(accountId));
    }

    @Test
    @DisplayName("credits the balance only once when the same InterestCalculated event is published twice")
    void creditsTheBalanceOnlyOnceWhenTheSameInterestCalculatedEventIsPublishedTwice() throws Exception {
        Account account = aSavedAccount("9080706015");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        String payload = anInterestCalculated(accountId, eventId, "35.00");

        publishedResult(accountId, payload);
        publishCalculated(accountId, payload);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertEquals(
                        new BigDecimal("1035.00"),
                        accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount()));
        assertEquals(1, count("outbox_events", eventId));
        assertEquals(1, count("processed_interest_events", eventId));
    }

    @Test
    @DisplayName("publishes InterestCreditRejected when the year was already credited over HTTP")
    void publishesInterestCreditRejectedWhenTheYearWasAlreadyCreditedOverHttp() throws Exception {
        Account account = aSavedAccount("9080706016");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        interestSummaryRepository.save(AnnualInterestSummary.create(
                Id.generate(),
                account.getId(),
                2025,
                Money.create(new BigDecimal("1000.00"), "USD"),
                Money.create(new BigDecimal("1035.00"), "USD"),
                new BigDecimal("0.0350"),
                Money.create(new BigDecimal("35.00"), "USD")));

        JsonNode result = publishedResult(accountId, anInterestCalculated(accountId, eventId, "35.00"));

        assertEquals("InterestCreditRejected", result.get("eventType").asText());
        assertTrue(result.get("reason").asText().contains("Interest already credited"));
        assertEquals(
                new BigDecimal("1000.00"),
                accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
    }

    @Test
    @DisplayName("credits an InterestCalculated event published while the listener was stopped after it restarts")
    void creditsAnInterestCalculatedEventPublishedWhileTheListenerWasStoppedAfterItRestarts() throws Exception {
        Account account = aSavedAccount("9080706017");
        String accountId = account.getId().getValue();
        String eventId = "interest:" + accountId + ":2025";
        MessageListenerContainer listener = calculatedListener();

        try {
            listener.stop();
            publishCalculated(accountId, anInterestCalculated(accountId, eventId, "35.00"));
            listener.start();

            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
                assertEquals(
                        new BigDecimal("1035.00"),
                        accountRepository.findById(account.getId()).orElseThrow().getBalance().getAmount());
                assertEquals(true, publishedFlag(eventId));
            });
        } finally {
            if (!listener.isRunning()) {
                listener.start();
            }
        }
    }

    private MessageListenerContainer calculatedListener() {
        return listenerRegistry.getListenerContainers().stream()
                .filter(container -> container.getContainerProperties().getTopics() != null)
                .filter(container -> List.of(container.getContainerProperties().getTopics())
                        .contains("interests.calculated"))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode publishedResult(String accountId, String payload) throws Exception {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> results = resultsConsumer(accountId);
                KafkaProducer<String, String> calculated = calculatedProducer()) {
            results.subscribe(List.of("interests.credit-results"));
            calculated.send(new ProducerRecord<>("interests.calculated", accountId, payload)).get();

            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                results.poll(Duration.ofMillis(500)).forEach(received::add);
                return received.stream().anyMatch(record -> accountId.equals(record.key()));
            });
        }

        ConsumerRecord<String, String> published = received.stream()
                .filter(record -> accountId.equals(record.key()))
                .findFirst()
                .orElseThrow();
        assertEquals(accountId, published.key());
        return objectMapper.readTree(published.value());
    }

    private void publishCalculated(String accountId, String payload) throws Exception {
        try (KafkaProducer<String, String> calculated = calculatedProducer()) {
            calculated.send(new ProducerRecord<>("interests.calculated", accountId, payload)).get();
        }
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

    private KafkaProducer<String, String> calculatedProducer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(properties);
    }

    private KafkaConsumer<String, String> resultsConsumer(String accountId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "interest-credit-results-it-" + accountId);
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

    private int count(String table, String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?",
                Integer.class,
                eventId);
        return count == null ? 0 : count;
    }

    private int countAccounts(String accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ?",
                Integer.class,
                UUID.fromString(accountId));
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
}
