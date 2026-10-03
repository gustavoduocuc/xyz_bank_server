package cl.duoc.xyzbank.coreservice.events.integration;

import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DisplayName("The saga topics core-service declares")
class KafkaTopicsIT extends AbstractKafkaPostgresIT {

    /*
     * Cases (event-messaging spec, "Every topic is partitioned and keyed by account" and
     * "Dead-letter topics exist for every topic and are partitioned like it"):
     * 1. The three topics and their three dead-letter topics all exist with 3 partitions
     */

    private static final List<String> TOPICS = List.of(
            "interests.calculated", "interests.credit-results", "transactions.confirmed",
            "interests.calculated.DLT", "interests.credit-results.DLT", "transactions.confirmed.DLT");

    @Test
    @DisplayName("exist with three partitions each, dead-letter topics included")
    void existWithThreePartitionsEachDeadLetterTopicsIncluded() throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());

        try (AdminClient admin = AdminClient.create(properties)) {
            Map<String, TopicDescription> topics = admin.describeTopics(TOPICS).allTopicNames().get();

            TOPICS.forEach(topic -> assertEquals(
                    3, topics.get(topic).partitions().size(), topic + " partitions"));
        }
    }
}
