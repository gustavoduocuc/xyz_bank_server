package cl.duoc.xyzbank.interestsservice.interests.integration;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"interests.credit-results"})
@DisplayName("The interest credit result listener")
class InterestCreditResultKafkaIT {

    /*
     * Cases:
     * 1. An InterestCreditApplied result closes the pending calculation as applied
     * 2. An InterestCreditRejected result closes the pending calculation as rejected with its reason
     */

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.kafka.consumer.group-id", () -> "interests-service-credit-results-it");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("interests.kafka.enabled", () -> "true");
        registry.add("interests.kafka.calculated-topic", () -> "interests.calculated");
        registry.add("interests.kafka.credit-results-topic", () -> "interests.credit-results");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.loadbalancer.enabled", () -> "false");
    }

    @Autowired
    private InterestCalculationRepository calculations;

    @Value("${spring.embedded.kafka.brokers}")
    private String bootstrapServers;

    @Test
    @DisplayName("closes the calculation as applied when InterestCreditApplied arrives")
    void closesTheCalculationAsAppliedWhenInterestCreditAppliedArrives() throws Exception {
        String eventId = "interest:account-applied:2025";
        calculations.save(InterestCalculation.pending(
                eventId, "account-applied", 2025, new BigDecimal("35.00"), "USD"));

        publishResult("""
                {
                  "eventId": "%s",
                  "eventType": "InterestCreditApplied",
                  "schemaVersion": 1,
                  "accountId": "account-applied",
                  "period": 2025,
                  "amount": "35.00",
                  "currency": "USD",
                  "occurredAt": "2026-01-15"
                }
                """.formatted(eventId), "account-applied");

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            InterestCalculation calculation = calculations.findByEventId(eventId).orElseThrow();
            assertEquals(InterestCalculationStatus.APPLIED, calculation.status());
            assertNull(calculation.reason());
        });
    }

    @Test
    @DisplayName("closes the calculation as rejected when InterestCreditRejected arrives")
    void closesTheCalculationAsRejectedWhenInterestCreditRejectedArrives() throws Exception {
        String eventId = "interest:account-rejected:2025";
        calculations.save(InterestCalculation.pending(
                eventId, "account-rejected", 2025, new BigDecimal("10.00"), "USD"));

        publishResult("""
                {
                  "eventId": "%s",
                  "eventType": "InterestCreditRejected",
                  "schemaVersion": 1,
                  "accountId": "account-rejected",
                  "period": 2025,
                  "reason": "Account account-rejected not found"
                }
                """.formatted(eventId), "account-rejected");

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            InterestCalculation calculation = calculations.findByEventId(eventId).orElseThrow();
            assertEquals(InterestCalculationStatus.REJECTED, calculation.status());
            assertEquals("Account account-rejected not found", calculation.reason());
        });
    }

    private void publishResult(String payload, String accountId) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>("interests.credit-results", accountId, payload)).get();
        }
    }
}
