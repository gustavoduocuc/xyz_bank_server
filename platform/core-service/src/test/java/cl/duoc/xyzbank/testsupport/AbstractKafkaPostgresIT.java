package cl.duoc.xyzbank.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractKafkaPostgresIT extends AbstractPostgresIT {

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
        registry.add("interests.outbox.relay-delay-ms", () -> "200");
    }
}
