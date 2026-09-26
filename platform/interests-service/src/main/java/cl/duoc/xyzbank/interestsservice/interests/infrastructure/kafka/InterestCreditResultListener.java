package cl.duoc.xyzbank.interestsservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCreditResult;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.RecordInterestCreditResultUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCreditResultListener {

    private final RecordInterestCreditResultUseCase recordInterestCreditResultUseCase;
    private final ObjectMapper objectMapper;

    public InterestCreditResultListener(
            RecordInterestCreditResultUseCase recordInterestCreditResultUseCase,
            ObjectMapper objectMapper) {
        this.recordInterestCreditResultUseCase = recordInterestCreditResultUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${interests.kafka.credit-results-topic}")
    public void onCreditResult(String payload) throws IOException {
        JsonNode result = objectMapper.readTree(payload);
        String eventType = result.get("eventType").asText();
        InterestCalculationStatus status = "InterestCreditRejected".equals(eventType)
                ? InterestCalculationStatus.REJECTED
                : InterestCalculationStatus.APPLIED;
        String reason = result.hasNonNull("reason") ? result.get("reason").asText() : null;
        recordInterestCreditResultUseCase.execute(new InterestCreditResult(
                result.get("eventId").asText(), status, reason));
    }
}
