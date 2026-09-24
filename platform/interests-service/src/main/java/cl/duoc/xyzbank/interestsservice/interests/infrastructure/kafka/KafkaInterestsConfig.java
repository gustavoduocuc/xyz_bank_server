package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class KafkaInterestsConfig {

    @Bean
    public NewTopic interestsCalculatedTopic(@Value("${interests.kafka.calculated-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCreditResultsTopic(@Value("${interests.kafka.credit-results-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
