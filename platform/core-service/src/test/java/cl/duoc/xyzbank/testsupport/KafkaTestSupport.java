package cl.duoc.xyzbank.testsupport;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/** Reads topics of the test broker the way an independent consumer would. */
public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    public static KafkaConsumer<String, String> consumer(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    public static void send(String bootstrapServers, String topic, String key, String payload) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish to " + topic, exception);
        }
    }

    /**
     * The first {@code count} records on {@code topic} carrying {@code key}, in the order the
     * broker holds them (records with one key sit on one partition, so that is publication order).
     */
    public static List<ConsumerRecord<String, String>> recordsWithKey(
            String bootstrapServers, String topic, String key, int count) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers)) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        matching.add(record);
                    }
                });
                return matching.size() >= count;
            });
        }
        return matching;
    }

    /** The records on {@code topic} carrying {@code key} after waiting {@code wait} for stragglers. */
    public static List<ConsumerRecord<String, String>> recordsWithKeyAfter(
            String bootstrapServers, String topic, String key, Duration wait) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(300)).forEach(record -> {
                    if (key.equals(record.key())) {
                        matching.add(record);
                    }
                });
            }
        }
        return matching;
    }
}
