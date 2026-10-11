package cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.sessions.application.ports.SecurityAlertPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes SecurityAlertRaised on security.alerts, keyed by customer. Best effort: a broker
 * problem is logged and never changes the outcome of the request that raised the alert (the
 * login is already revoked).
 */
public class KafkaSecurityAlertPublisher implements SecurityAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaSecurityAlertPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaSecurityAlertPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void refreshTokenReuse(String customerId) {
        try {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("eventId", UUID.randomUUID().toString());
            alert.put("eventType", "SecurityAlertRaised");
            alert.put("schemaVersion", 1);
            alert.put("alertType", "REFRESH_TOKEN_REUSE");
            alert.put("customerId", customerId);
            alert.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
            kafkaTemplate.send(topic, customerId, objectMapper.writeValueAsString(alert));
        } catch (Exception exception) {
            log.warn("Could not publish the REFRESH_TOKEN_REUSE alert for a customer", exception);
        }
    }
}
