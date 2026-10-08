package cl.duoc.xyzbank.coreservice.events.integration;

import cl.duoc.xyzbank.coreservice.events.infrastructure.kafka.OutboxEventRelay;
import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("The outbox relay when a send fails")
class OutboxEventRelayFailureIT extends AbstractCoreServiceIT {

    /*
     * Cases (event-messaging spec, "Outbox events are published in the order they were written"),
     * with the relay driven directly and its Kafka sender replaced by a stub that fails chosen sends:
     * 1. When an account's first event fails to send, its second event is not sent before it,
     *    another account's event is not held back, and the next run sends both in order
     * 2. An event already published is not sent again
     */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final RecordingKafkaTemplate sender = new RecordingKafkaTemplate();
    private OutboxEventRelay relay;

    @BeforeEach
    void isolateFromOtherTests() {
        jdbcTemplate.update("UPDATE outbox_events SET published = TRUE");
        relay = new OutboxEventRelay(
                jdbcTemplate, sender, objectMapper, transactionManager, "interests.credit-results", "transactions.confirmed");
    }

    @Test
    @DisplayName("does not let a later event of an account overtake one that failed, and does not hold back other accounts")
    void doesNotLetALaterEventOfAnAccountOvertakeOneThatFailedAndDoesNotHoldBackOtherAccounts() {
        String accountA = UUID.randomUUID().toString();
        String accountB = UUID.randomUUID().toString();
        String a1 = "a1-" + accountA;
        String a2 = "a2-" + accountA;
        String b1 = "b1-" + accountB;
        insert(accountA, a1);
        insert(accountA, a2);
        insert(accountB, b1);
        sender.failFirstSendOf(a1);

        relay.publishPending();

        assertEquals(List.of(b1), sender.sentEventIds(), "account B flows, account A is held at its failed event");
        assertTrue(!published(a1) && !published(a2) && published(b1));

        relay.publishPending();

        assertEquals(List.of(b1, a1, a2), sender.sentEventIds());
        assertTrue(published(a1) && published(a2));
    }

    @Test
    @DisplayName("does not send an event that was already published")
    void doesNotSendAnEventThatWasAlreadyPublished() {
        String account = UUID.randomUUID().toString();
        String event = "once-" + account;
        insert(account, event);

        relay.publishPending();
        relay.publishPending();

        assertEquals(List.of(event), sender.sentEventIds());
    }

    private void insert(String accountId, String eventId) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (id, event_id, event_type, schema_version, account_id,
                                           amount, currency, occurred_on, movement_type)
                VALUES (?, ?, 'TransactionConfirmed', 1, ?, 10.00, 'USD', ?, 'WITHDRAWAL')
                """,
                UUID.randomUUID(), eventId, UUID.fromString(accountId), LocalDate.of(2026, 1, 15));
    }

    private boolean published(String eventId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT published FROM outbox_events WHERE event_id = ?", Boolean.class, eventId));
    }

    /** Records what the relay sends, in order, and fails chosen sends once. Needs no broker. */
    private static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {

        private final List<String> sent = new ArrayList<>();
        private final Set<String> failOnce = new HashSet<>();
        private final ObjectMapper mapper = new ObjectMapper();

        RecordingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of()));
        }

        void failFirstSendOf(String eventId) {
            failOnce.add(eventId);
        }

        List<String> sentEventIds() {
            return List.copyOf(sent);
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            try {
                String eventId = mapper.readTree(data).get("eventId").asText();
                if (failOnce.remove(eventId)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
                }
                sent.add(eventId);
                return CompletableFuture.completedFuture(null);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
    }
}
