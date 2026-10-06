package cl.duoc.xyzbank.interestsservice.interests.unit;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculation;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InMemoryInterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The in-memory interest calculation repository")
class InMemoryInterestCalculationRepositoryTest {

    /*
     * Cases (design.md Decision 2: three listener threads share it with the request threads):
     * 1. Many threads saving at the same time lose no calculation
     * 2. Threads finding while others save never fail
     */

    private static final int THREADS = 8;
    private static final int PER_THREAD = 3_000;

    private final InterestCalculationRepository calculations = new InMemoryInterestCalculationRepository();

    @Test
    @DisplayName("loses no calculation when many threads save at the same time")
    void losesNoCalculationWhenManyThreadsSaveAtTheSameTime() throws Exception {
        runConcurrently(thread -> {
            for (int i = 0; i < PER_THREAD; i++) {
                calculations.save(aCalculation(thread, i));
            }
        });

        for (int thread = 0; thread < THREADS; thread++) {
            for (int i = 0; i < PER_THREAD; i++) {
                assertTrue(calculations.findByEventId(eventId(thread, i)).isPresent(), eventId(thread, i));
            }
        }
    }

    @Test
    @DisplayName("finds calculations while other threads save")
    void findsCalculationsWhileOtherThreadsSave() throws Exception {
        runConcurrently(thread -> {
            for (int i = 0; i < PER_THREAD; i++) {
                calculations.save(aCalculation(thread, i));
                assertEquals(
                        eventId(thread, i), calculations.findByEventId(eventId(thread, i)).orElseThrow().eventId());
            }
        });
    }

    private void runConcurrently(ThreadBody body) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();
        for (int thread = 0; thread < THREADS; thread++) {
            int number = thread;
            running.add(executor.submit(() -> {
                start.await();
                body.run(number);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : running) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdownNow();
    }

    private static InterestCalculation aCalculation(int thread, int index) {
        return InterestCalculation.pending(
                eventId(thread, index), "account-" + thread, 2025, new BigDecimal("1.00"), "USD");
    }

    private static String eventId(int thread, int index) {
        return "interest:" + thread + ":" + index;
    }

    private interface ThreadBody {
        void run(int thread) throws Exception;
    }
}
