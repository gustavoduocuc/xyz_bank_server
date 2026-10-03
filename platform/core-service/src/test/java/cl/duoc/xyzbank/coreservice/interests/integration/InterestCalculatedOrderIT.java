package cl.duoc.xyzbank.coreservice.interests.integration;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.testsupport.AbstractKafkaPostgresIT;
import cl.duoc.xyzbank.testsupport.KafkaTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DisplayName("core-service's consumption of interests.calculated")
class InterestCalculatedOrderIT extends AbstractKafkaPostgresIT {

    /*
     * Cases (event-messaging spec, "Consumer concurrency matches the partition count" and "The
     * saga events of one account are processed in order"):
     * 1. The listener runs 3 consumers, each assigned one of the topic's 3 partitions
     * 2. InterestCalculated events of one account for several years, published in ascending
     *    order, produce their results in that order
     * 3. The same holds for events published while the listener is stopped and consumed after
     *    it restarts
     */

    private static final String CALCULATED = "interests.calculated";
    private static final String RESULTS = "interests.credit-results";
    private static final List<Integer> YEARS = List.of(2021, 2022, 2023, 2024, 2025);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("runs one consumer per partition")
    void runsOneConsumerPerPartition() {
        ConcurrentMessageListenerContainer<?, ?> listener = interestCalculatedListener();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(3, listener.getConcurrency());
            List<Set<TopicPartition>> assignments = listener.getContainers().stream()
                    .map(container -> Set.copyOf(container.getAssignedPartitions()))
                    .toList();
            assertEquals(3, assignments.size());
            assertEquals(3, assignments.stream().flatMap(Set::stream).collect(Collectors.toSet()).size());
            assignments.forEach(assignment -> assertEquals(1, assignment.size()));
        });
    }

    @Test
    @DisplayName("processes the events of one account in the order they were published")
    void processesTheEventsOfOneAccountInTheOrderTheyWerePublished() throws Exception {
        Account account = aSavedAccount("9080706201");
        String accountId = account.getId().getValue();

        publishYears(accountId);

        assertEquals(YEARS, resultPeriods(accountId));
    }

    @Test
    @DisplayName("processes events published while the listener was stopped in publication order")
    void processesEventsPublishedWhileTheListenerWasStoppedInPublicationOrder() throws Exception {
        Account account = aSavedAccount("9080706202");
        String accountId = account.getId().getValue();
        MessageListenerContainer listener = interestCalculatedListener();
        listener.stop();

        publishYears(accountId);
        listener.start();

        assertEquals(YEARS, resultPeriods(accountId));
    }

    private ConcurrentMessageListenerContainer<?, ?> interestCalculatedListener() {
        return listenerRegistry.getListenerContainers().stream()
                .filter(container -> container instanceof ConcurrentMessageListenerContainer<?, ?>)
                .map(container -> (ConcurrentMessageListenerContainer<?, ?>) container)
                .filter(container -> List.of(container.getContainerProperties().getTopics()).contains(CALCULATED))
                .findFirst()
                .orElseThrow();
    }

    private void publishYears(String accountId) {
        YEARS.forEach(year -> KafkaTestSupport.send(
                KAFKA.getBootstrapServers(), CALCULATED, accountId, anInterestCalculated(accountId, year)));
    }

    private List<Integer> resultPeriods(String accountId) {
        return KafkaTestSupport.recordsWithKey(KAFKA.getBootstrapServers(), RESULTS, accountId, YEARS.size())
                .stream()
                .map(ConsumerRecord::value)
                .map(value -> {
                    try {
                        return objectMapper.readTree(value).get("period").asInt();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
    }

    private String anInterestCalculated(String accountId, int year) {
        return """
                {
                  "eventId": "interest:%s:%d",
                  "eventType": "InterestCalculated",
                  "schemaVersion": 1,
                  "accountId": "%s",
                  "period": %d,
                  "amount": "10.00",
                  "currency": "USD",
                  "interestRate": "0.0100",
                  "openingBalance": "1000.00",
                  "closingBalance": "1010.00",
                  "occurredAt": "2026-01-15"
                }
                """.formatted(accountId, year, accountId, year);
    }

    private Account aSavedAccount(String accountNumber) {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Order Customer", customerId.getValue() + "@xyzbank.cl"));
        Account account = Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("1000.00"), "USD"));
        accountRepository.save(account);
        return accountRepository.findById(account.getId()).orElseThrow();
    }
}
