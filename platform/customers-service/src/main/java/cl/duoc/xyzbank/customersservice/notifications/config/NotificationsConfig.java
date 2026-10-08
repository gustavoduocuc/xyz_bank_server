package cl.duoc.xyzbank.customersservice.notifications.config;

import cl.duoc.xyzbank.customersservice.notifications.application.GetNotificationsUseCase;
import cl.duoc.xyzbank.customersservice.notifications.application.RecordNotificationUseCase;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;
import cl.duoc.xyzbank.customersservice.notifications.infrastructure.persistence.JdbcNotificationRepository;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class NotificationsConfig {

    static final String DEAD_LETTER_SUFFIX = ".DLT";

    @Bean
    public NotificationRepository notificationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationRepository(jdbcTemplate);
    }

    @Bean
    public RecordNotificationUseCase recordNotificationUseCase(NotificationRepository notifications) {
        return new RecordNotificationUseCase(notifications);
    }

    @Bean
    public GetNotificationsUseCase getNotificationsUseCase(NotificationRepository notifications) {
        return new GetNotificationsUseCase(notifications);
    }

    /**
     * A record that keeps failing is retried (blocking, so the records behind it keep their order)
     * and then published to {@code <topic>.DLT}, and the partition moves on. Redelivery is safe:
     * the event id makes recording idempotent.
     */
    @Bean
    public CommonErrorHandler notificationsErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${notifications.kafka.retry.max-retries:3}") int maxRetries,
            @Value("${notifications.kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${notifications.kafka.retry.multiplier:2.0}") double multiplier) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        return new DefaultErrorHandler(deadLetters, backOff);
    }
}
