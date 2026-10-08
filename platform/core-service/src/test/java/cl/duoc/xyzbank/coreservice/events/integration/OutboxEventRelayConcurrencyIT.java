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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("Concurrent outbox relays")
class OutboxEventRelayConcurrencyIT extends AbstractCoreServiceIT {

    /*
     * Cases (event-messaging spec, "Concurrent outbox relays publish each event once"),
     * with two relays driven on two threads and a slow stub in place of Kafka:
     * 1. Every pending event is sent exactly once and ends up marked published
     * 2. An event whose send failed is not lost: the next run sends it once
     */

    private static final int EVENTS = 30;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final SlowRecordingKafkaTemplate sender = new SlowRecordingKafkaTemplate();

    @BeforeEach
    void isolateFromOtherTests() {
        jdbcTemplate.update("UPDATE outbox_events SET published = TRUE");
    }

    @Test
    @DisplayName("send every pending event exactly once when two relays run at the same time")
    void sendEveryPendingEventExactlyOnceWhenTwoRelaysRunAtTheSameTime() throws Exception {
        List<String> eventIds = insertPendingEvents(EVENTS);

        runTwoRelaysConcurrently();

        assertEquals(EVENTS, sender.sent.size(), "no event sent twice, none missing");
        assertTrue(sender.sent.containsAll(eventIds));
        assertEquals(0, unpublishedCount());
    }

    @Test
    @DisplayName("leave an event whose send failed for the next run, which sends it once")
    void leaveAnEventWhoseSendFailedForTheNextRunWhichSendsItOnce() throws Exception {
        List<String> eventIds = insertPendingEvents(2);
        sender.failFirstSendOf(eventIds.get(0));

        runTwoRelaysConcurrently();
        newRelay().publishPending();

        assertEquals(2, sender.sent.size());
        assertTrue(sender.sent.containsAll(eventIds));
        assertEquals(0, unpublishedCount());
    }

    private void runTwoRelaysConcurrently() throws Exception {
        OutboxEventRelay first = newRelay();
        OutboxEventRelay second = newRelay();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Void>> runs = List.of(first, second).stream()
                    .map(relay -> CompletableFuture.runAsync(() -> {
                        await(start);
                        relay.publishPending();
                    }, executor))
                    .toList();
            start.countDown();
            runs.forEach(CompletableFuture::join);
        } finally {
            executor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private OutboxEventRelay newRelay() {
        return new OutboxEventRelay(
                jdbcTemplate, sender, objectMapper, transactionManager,
                "interests.credit-results", "transactions.confirmed");
    }

    private List<String> insertPendingEvents(int count) {
        List<String> eventIds = new CopyOnWriteArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID account = UUID.randomUUID();
            String eventId = "concurrent-" + account;
            jdbcTemplate.update(
                    """
                    INSERT INTO outbox_events (id, event_id, event_type, schema_version, account_id,
                                               amount, currency, occurred_on, movement_type)
                    VALUES (?, ?, 'TransactionConfirmed', 1, ?, 10.00, 'USD', ?, 'WITHDRAWAL')
                    """,
                    UUID.randomUUID(), eventId, account, LocalDate.of(2026, 1, 15));
            eventIds.add(eventId);
        }
        return eventIds;
    }

    private int unpublishedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE published = FALSE", Integer.class);
    }

    /** Records what is sent and takes a moment per send, so two unlocked relays would overlap. */
    private static final class SlowRecordingKafkaTemplate extends KafkaTemplate<String, String> {

        private final List<String> sent = new CopyOnWriteArrayList<>();
        private final List<String> failOnce = new CopyOnWriteArrayList<>();
        private final ObjectMapper mapper = new ObjectMapper();

        SlowRecordingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of()));
        }

        void failFirstSendOf(String eventId) {
            failOnce.add(eventId);
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            try {
                Thread.sleep(20);
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
