package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class KafkaInterestsConfig {

    static final String DEAD_LETTER_SUFFIX = ".DLT";

    @Bean
    public NewTopic interestsCalculatedTopic(
            @Value("${interests.kafka.calculated-topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCalculatedDeadLetterTopic(
            @Value("${interests.kafka.calculated-topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCreditResultsTopic(
            @Value("${interests.kafka.credit-results-topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCreditResultsDeadLetterTopic(
            @Value("${interests.kafka.credit-results-topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(1).build();
    }

    /**
     * A record that keeps failing is retried a bounded number of times (blocking, so the records
     * behind it on the partition keep their order), then published to {@code <topic>.DLT} with its
     * key, payload and the failure in its headers, and the partition moves on. The partition is
     * chosen by the key, so the dead-letter topic need not match the source topic's partition count.
     * Redelivery is safe: crediting is idempotent on the event id. Business rejections are not
     * failures; they are reported as InterestCreditRejected and never reach this handler.
     */
    @Bean
    public CommonErrorHandler interestCalculatedErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${interests.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${interests.kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${interests.kafka.retry.multiplier:2.0}") double multiplier) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        // kafka.dlt.messages{topic}: one increment per record sent to its dead-letter topic
        ConsumerRecordRecoverer countedDeadLetters = (record, exception) -> {
            meterRegistry.counter("kafka.dlt.messages", "topic", record.topic()).increment();
            deadLetters.accept(record, exception);
        };
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        return new DefaultErrorHandler(countedDeadLetters, backOff);
    }
}
