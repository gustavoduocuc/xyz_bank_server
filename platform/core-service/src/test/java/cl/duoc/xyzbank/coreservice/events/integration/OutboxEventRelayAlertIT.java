package cl.duoc.xyzbank.coreservice.events.integration;

import cl.duoc.xyzbank.coredomain.cards.domain.entities.Card;
import cl.duoc.xyzbank.coredomain.cards.domain.repositories.CardRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.auth.application.usecases.VerifyPinUseCase;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import cl.duoc.xyzbank.testsupport.KafkaTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("Security alerts published by the outbox relay")
class OutboxEventRelayAlertIT extends AbstractKafkaPostgresIT {

    /*
     * Cases (security-alerts spec):
     * 1. The failure that locks a card publishes one CARD_LOCKED on security.alerts, keyed by the
     *    customer, without card number or PIN
     * 2. Further attempts on the locked card publish no second alert
     */

    private static final String TOPIC = "security.alerts";

    @Autowired
    private VerifyPinUseCase verifyPinUseCase;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void enableSecurityAlerts(DynamicPropertyRegistry registry) {
        registry.add("app.events.security-alerts.enabled", () -> "true");
    }

    @Test
    @DisplayName("publishes one CARD_LOCKED alert keyed by the customer when a card gets locked")
    void publishesOneCardLockedAlertKeyedByTheCustomerWhenACardGetsLocked() throws Exception {
        Id cardId = Id.generate();
        String customerId = lockableCardOf(cardId);

        verifyPinUseCase.execute(cardId.getValue(), "0000");

        List<ConsumerRecord<String, String>> records =
                KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), TOPIC, customerId, 1);
        JsonNode alert = objectMapper.readTree(records.getFirst().value());
        assertEquals("SecurityAlertRaised", alert.get("eventType").asText());
        assertEquals(1, alert.get("schemaVersion").asInt());
        assertEquals("CARD_LOCKED", alert.get("alertType").asText());
        assertEquals(customerId, alert.get("customerId").asText());
        assertTrue(alert.hasNonNull("eventId") && alert.hasNonNull("occurredAt"));
        assertFalse(records.getFirst().value().contains(cardId.getValue()), "no card number in the alert");
    }

    @Test
    @DisplayName("publishes no second alert for further attempts on the locked card")
    void publishesNoSecondAlertForFurtherAttemptsOnTheLockedCard() {
        Id cardId = Id.generate();
        String customerId = lockableCardOf(cardId);

        verifyPinUseCase.execute(cardId.getValue(), "0000");
        verifyPinUseCase.execute(cardId.getValue(), "0000");
        verifyPinUseCase.execute(cardId.getValue(), "1234");

        List<ConsumerRecord<String, String>> records = KafkaTestSupport.recordsWithKeyAfter(
                KAFKA.getBootstrapServers(), TOPIC, customerId, Duration.ofSeconds(5));
        assertEquals(1, records.size());
    }

    /** A card one wrong PIN away from being locked; returns its customer's id. */
    private String lockableCardOf(Id cardId) {
        Id customerId = Id.generate();
        cardRepository.save(Card.create(
                cardId, customerId, new cl.duoc.xyzbank.sharedsecurity.callercontext.PinHasher().hash("1234"), 2, false, 0L));
        return customerId.getValue();
    }
}
