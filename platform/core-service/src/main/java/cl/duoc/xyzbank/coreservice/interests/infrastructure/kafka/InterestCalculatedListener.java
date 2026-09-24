package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import cl.duoc.xyzbank.coreservice.interests.application.dto.CreditInterestRequest;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class InterestCalculatedListener {

    private final CreditInterestUseCase creditInterestUseCase;
    private final ObjectMapper objectMapper;

    public InterestCalculatedListener(CreditInterestUseCase creditInterestUseCase, ObjectMapper objectMapper) {
        this.creditInterestUseCase = creditInterestUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${interests.kafka.calculated-topic}")
    public void onInterestCalculated(String payload) throws IOException {
        InterestCalculated event = objectMapper.readValue(payload, InterestCalculated.class);
        creditInterestUseCase.executeFromEvent(toRequest(event), event.eventId());
    }

    private CreditInterestRequest toRequest(InterestCalculated event) {
        return new CreditInterestRequest(
                event.accountId(),
                event.period(),
                event.amount(),
                event.currency(),
                event.interestRate(),
                event.openingBalance(),
                event.closingBalance(),
                event.eventId());
    }
}
