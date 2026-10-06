package cl.duoc.xyzbank.testsupport;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Each subclass's Spring context is closed after its class. The contexts share one broker, one
 * database and one consumer group, so a context left running by an earlier class keeps consuming
 * a share of the topic's partitions and would process records meant for the running test.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractKafkaPostgresIT extends AbstractCoreServiceIT {

    protected static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.1"));

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("interests.kafka.enabled", () -> "true");
        registry.add("app.events.transaction-confirmed.enabled", () -> "true");
        registry.add("app.outbox.relay-delay-ms", () -> "200");
    }
}
