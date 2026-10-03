package cl.duoc.xyzbank.coreservice.events.integration;

import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import cl.duoc.xyzbank.testsupport.KafkaTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DisplayName("The outbox relay's publication order")
class OutboxEventRelayOrderIT extends AbstractKafkaPostgresIT {

    /*
     * Cases (event-messaging spec, "Outbox events are published in the order they were written"):
     * 1. Two events of one account on the same day, whose ids sort opposite to their write
     *    order, are published in write order
     */

    private static final String TOPIC = "transactions.confirmed";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("publishes two events of one account on the same day in the order they were written")
    void publishesTwoEventsOfOneAccountOnTheSameDayInTheOrderTheyWereWritten() throws Exception {
        String accountId = UUID.randomUUID().toString();
        UUID writtenFirst = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
        UUID writtenSecond = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // One statement, so the relay sees both rows at once and cannot publish the first alone
        insertTogether(accountId, writtenFirst, "order-first-" + accountId, writtenSecond, "order-second-" + accountId);

        List<ConsumerRecord<String, String>> records = KafkaTestSupport.recordsWithKey(
                KAFKA.getBootstrapServers(), TOPIC, accountId, 2);
        assertEquals("order-first-" + accountId, eventIdOf(records.get(0)));
        assertEquals("order-second-" + accountId, eventIdOf(records.get(1)));
    }

    private void insertTogether(String accountId, UUID firstId, String firstEvent, UUID secondId, String secondEvent) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (id, event_id, event_type, schema_version, account_id,
                                           amount, currency, occurred_on, movement_type)
                VALUES (?, ?, 'TransactionConfirmed', 1, ?, 10.00, 'USD', ?, 'WITHDRAWAL'),
                       (?, ?, 'TransactionConfirmed', 1, ?, 20.00, 'USD', ?, 'WITHDRAWAL')
                """,
                firstId, firstEvent, UUID.fromString(accountId), LocalDate.of(2026, 1, 15),
                secondId, secondEvent, UUID.fromString(accountId), LocalDate.of(2026, 1, 15));
    }

    private String eventIdOf(ConsumerRecord<String, String> record) throws Exception {
        return objectMapper.readTree(record.value()).get("eventId").asText();
    }
}
