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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@EnableScheduling
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

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("interest-outbox-relay-");
        threadFactory.setDaemon(true);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadFactory(threadFactory);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public CommonErrorHandler interestCalculatedErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
