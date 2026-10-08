package cl.duoc.xyzbank.customersservice.notifications.integration;

import cl.duoc.xyzbank.customersservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.customersservice.testsupport.TestTokens;
import io.restassured.RestAssured;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsKafkaIT extends AbstractPostgresIT {

    /*
     * Cases (customer-notifications spec):
     * 1. A card-lock alert and a withdrawal confirmation, as core-service publishes them, appear
     *    in the customer's feed, newest first
     * 2. An event delivered twice leaves one entry
     * 3. A record that cannot be processed is retried and then published to the topic's .DLT
     */

    private static final String TRANSACTIONS = "transactions.confirmed";
    private static final String ALERTS = "security.alerts";

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    static {
        KAFKA.start();
        try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(TRANSACTIONS, 3, (short) 1), new NewTopic(ALERTS, 3, (short) 1),
                    new NewTopic(TRANSACTIONS + ".DLT", 3, (short) 1), new NewTopic(ALERTS + ".DLT", 3, (short) 1)))
                    .all().get();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the test topics", exception);
        }
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("notifications.kafka.retry.initial-interval-ms", () -> "100");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("shows a card lock and a withdrawal in the customer's feed")
    void showsACardLockAndAWithdrawalInTheCustomersFeed() {
        String customerId = UUID.randomUUID().toString();
        send(ALERTS, customerId, alert("alert-" + customerId, "CARD_LOCKED", customerId, "2026-10-08T12:00:00Z"));
        send(TRANSACTIONS, "account-1", withdrawal("tx-" + customerId, customerId, "account-1", "2026-10-09"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> feedOf(customerId)
                .body("kind", contains("TRANSACTION_CONFIRMED", "CARD_LOCKED"))
                .body("[0].type", org.hamcrest.Matchers.equalTo("WITHDRAWAL"))
                .body("[0].amount", org.hamcrest.Matchers.equalTo(40.00f)));
    }

    @Test
    @DisplayName("keeps one entry when an event is delivered twice")
    void keepsOneEntryWhenAnEventIsDeliveredTwice() {
        String customerId = UUID.randomUUID().toString();
        String lock = alert("alert-" + customerId, "CARD_LOCKED", customerId, "2026-10-08T12:00:00Z");
        send(ALERTS, customerId, lock);
        send(ALERTS, customerId, lock);
        // Same key, same partition: once the marker is stored, the duplicate before it was processed
        send(ALERTS, customerId, alert("marker-" + customerId, "REFRESH_TOKEN_REUSE", customerId, "2026-10-08T13:00:00Z"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> feedOf(customerId)
                .body("kind", contains("REFRESH_TOKEN_REUSE", "CARD_LOCKED")));
        feedOf(customerId).body("$", hasSize(2));
    }

    @Test
    @DisplayName("publishes a record that cannot be processed to the topic's dead-letter topic")
    void publishesARecordThatCannotBeProcessedToTheTopicsDeadLetterTopic() {
        String customerId = UUID.randomUUID().toString();
        send(ALERTS, customerId, "this is not json");

        AtomicBoolean deadLettered = new AtomicBoolean(false);
        try (KafkaConsumer<String, String> consumer = consumerOf(ALERTS + ".DLT")) {
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> deadLettered.compareAndSet(false, customerId.equals(record.key())));
                return deadLettered.get();
            });
        }
        assertTrue(deadLettered.get());
    }

    private io.restassured.response.ValidatableResponse feedOf(String customerId) {
        return given().header("Authorization", "Bearer " + TestTokens.web(customerId))
                .get("/internal/customers/{id}/notifications", customerId)
                .then().statusCode(200);
    }

    private static String alert(String eventId, String alertType, String customerId, String occurredAt) {
        return """
                {"eventId":"%s","eventType":"SecurityAlertRaised","schemaVersion":1,"alertType":"%s",
                 "customerId":"%s","occurredAt":"%s"}""".formatted(eventId, alertType, customerId, occurredAt);
    }

    private static String withdrawal(String eventId, String customerId, String accountId, String occurredAt) {
        return """
                {"eventId":"%s","eventType":"TransactionConfirmed","schemaVersion":1,"accountId":"%s",
                 "customerId":"%s","type":"WITHDRAWAL","amount":40.00,"currency":"USD","occurredAt":"%s"}"""
                .formatted(eventId, accountId, customerId, occurredAt);
    }

    private static void send(String topic, String key, String payload) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish to " + topic, exception);
        }
    }

    private static KafkaConsumer<String, String> consumerOf(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "notifications-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
