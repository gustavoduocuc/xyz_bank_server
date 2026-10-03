package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

    @Bean
    public CommonErrorHandler interestCalculatedErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
