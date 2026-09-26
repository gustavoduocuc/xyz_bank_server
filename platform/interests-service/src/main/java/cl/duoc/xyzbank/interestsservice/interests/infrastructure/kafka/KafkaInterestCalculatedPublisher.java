package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCalculatedNotice;
import cl.duoc.xyzbank.interestsservice.interests.application.ports.InterestCalculatedPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class KafkaInterestCalculatedPublisher implements InterestCalculatedPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String calculatedTopic;

    public KafkaInterestCalculatedPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${interests.kafka.calculated-topic}") String calculatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.calculatedTopic = calculatedTopic;
    }

    @Override
    public void publish(InterestCalculatedNotice notice) {
        try {
            String payload = objectMapper.writeValueAsString(notice);
            kafkaTemplate.send(calculatedTopic, notice.accountId(), payload).get(5, TimeUnit.SECONDS);
        } catch (JsonProcessingException | ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Interest calculation " + notice.eventId() + " was not published", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interest calculation " + notice.eventId() + " was not published", exception);
        }
    }
}
