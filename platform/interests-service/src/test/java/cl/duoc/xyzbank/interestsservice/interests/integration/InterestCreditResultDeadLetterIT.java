package cl.duoc.xyzbank.interestsservice.interests.integration;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"interests.credit-results", "interests.credit-results.DLT"})
@DisplayName("interests-service's handling of credit results that fail")
class InterestCreditResultDeadLetterIT {

    /*
     * Cases (event-messaging spec, "A failing message is retried a bounded number of times and
     * then dead-lettered"), with the retry delays shortened to 50 ms / 100 ms / 200 ms:
     * 1. A record that is not a valid credit result ends on interests.credit-results.DLT with its
     *    key, payload and failure headers only after the retries, and the next valid result for
     *    the same account is recorded
     */

    private static final String RESULTS = "interests.credit-results";
    private static final String DEAD_LETTER = "interests.credit-results.DLT";
    private static final Duration TOTAL_BACKOFF = Duration.ofMillis(350);

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.kafka.consumer.group-id", () -> "interests-service-dead-letter-it");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("interests.kafka.enabled", () -> "true");
        registry.add("interests.kafka.calculated-topic", () -> "interests.calculated");
        registry.add("interests.kafka.credit-results-topic", () -> RESULTS);
        registry.add("interests.kafka.retry.max-retries", () -> "3");
        registry.add("interests.kafka.retry.initial-interval-ms", () -> "50");
        registry.add("interests.kafka.retry.multiplier", () -> "2.0");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.loadbalancer.enabled", () -> "false");
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private InterestCalculationRepository calculations;

    @Value("${spring.embedded.kafka.brokers}")
    private String bootstrapServers;

    @Test
    @DisplayName("dead-letters a record that is not a credit result and still records the next result")
    void deadLettersARecordThatIsNotACreditResultAndStillRecordsTheNextResult() {
        String accountId = "account-" + UUID.randomUUID();
        String eventId = "interest:" + accountId + ":2025";
        calculations.save(InterestCalculation.pending(eventId, accountId, 2025, new BigDecimal("35.00"), "USD"));
        String invalid = "this is not an InterestCreditApplied";
        long publishedAt = System.nanoTime();

        publish(accountId, invalid);
        publish(accountId, """
                {"eventId": "%s", "eventType": "InterestCreditApplied", "schemaVersion": 1,
                 "accountId": "%s", "period": 2025, "amount": "35.00", "currency": "USD"}
                """.formatted(eventId, accountId));

        ConsumerRecord<String, String> dead = awaitDeadLetter(accountId);
        Duration waited = Duration.ofNanos(System.nanoTime() - publishedAt);
        assertEquals(invalid, dead.value());
        assertEquals(RESULTS, header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC));
        assertNotNull(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN));
        assertTrue(waited.compareTo(TOTAL_BACKOFF) >= 0, "dead-lettered after " + waited + ", before the retries ran");
        assertTrue(meterRegistry.get("kafka.dlt.messages").tag("topic", RESULTS).counter().count() >= 1.0);
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertEquals(
                        InterestCalculationStatus.APPLIED,
                        calculations.findByEventId(eventId).orElseThrow().status()));
    }

    private void publish(String key, String payload) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(RESULTS, key, payload)).get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-reader-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DEAD_LETTER));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
                return !found.isEmpty();
            });
        }
        return found.getFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
