package cl.duoc.xyzbank.authserver.sessions.unit;

import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.KafkaSecurityAlertPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The Kafka security alert publisher")
class KafkaSecurityAlertPublisherTest {

    /*
     * Cases (security-alerts spec, "Replaying a refresh token raises REFRESH_TOKEN_REUSE"):
     * 1. Publishes SecurityAlertRaised with alertType REFRESH_TOKEN_REUSE on security.alerts, keyed by the customer
     * 2. A broker failure is absorbed: nothing is thrown to the caller
     */

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("publishes a REFRESH_TOKEN_REUSE alert keyed by the customer")
    void publishesARefreshTokenReuseAlertKeyedByTheCustomer() throws Exception {
        RecordingTemplate template = new RecordingTemplate(false);

        new KafkaSecurityAlertPublisher(template, objectMapper, "security.alerts").refreshTokenReuse("customer-1");

        assertEquals(List.of("security.alerts"), template.topics);
        assertEquals(List.of("customer-1"), template.keys);
        JsonNode alert = objectMapper.readTree(template.payloads.getFirst());
        assertEquals("SecurityAlertRaised", alert.get("eventType").asText());
        assertEquals(1, alert.get("schemaVersion").asInt());
        assertEquals("REFRESH_TOKEN_REUSE", alert.get("alertType").asText());
        assertEquals("customer-1", alert.get("customerId").asText());
        assertTrue(alert.hasNonNull("eventId") && alert.hasNonNull("occurredAt"));
    }

    @Test
    @DisplayName("absorbs a broker failure")
    void absorbsABrokerFailure() {
        KafkaSecurityAlertPublisher publisher =
                new KafkaSecurityAlertPublisher(new RecordingTemplate(true), objectMapper, "security.alerts");

        assertDoesNotThrow(() -> publisher.refreshTokenReuse("customer-1"));
    }

    /** Records what is sent; needs no broker. */
    private static final class RecordingTemplate extends KafkaTemplate<String, String> {

        private final List<String> topics = new ArrayList<>();
        private final List<String> keys = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();
        private final boolean failing;

        RecordingTemplate(boolean failing) {
            super(new DefaultKafkaProducerFactory<>(Map.of()));
            this.failing = failing;
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            if (failing) {
                throw new IllegalStateException("broker unavailable");
            }
            topics.add(topic);
            keys.add(key);
            payloads.add(data);
            return CompletableFuture.completedFuture(null);
        }
    }
}
