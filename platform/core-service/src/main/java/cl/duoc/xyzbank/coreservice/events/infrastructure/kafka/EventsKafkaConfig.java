package cl.duoc.xyzbank.coreservice.events.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnExpression(
        "${interests.kafka.enabled:false} || ${app.events.transaction-confirmed.enabled:false}")
public class EventsKafkaConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("outbox-relay-");
        threadFactory.setDaemon(true);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadFactory(threadFactory);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(name = "app.events.transaction-confirmed.enabled", havingValue = "true")
    public NewTopic transactionsConfirmedTopic(
            @Value("${app.events.transaction-confirmed.topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    /** No consumer in this repository reads the topic yet; a future one gets its destination from day one. */
    @Bean
    @ConditionalOnProperty(name = "app.events.transaction-confirmed.enabled", havingValue = "true")
    public NewTopic transactionsConfirmedDeadLetterTopic(
            @Value("${app.events.transaction-confirmed.topic}") String topic,
            @Value("${interests.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(1).build();
    }
}
