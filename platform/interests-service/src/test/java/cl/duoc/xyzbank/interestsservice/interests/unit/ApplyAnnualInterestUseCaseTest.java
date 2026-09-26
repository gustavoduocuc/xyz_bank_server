package cl.duoc.xyzbank.interestsservice.interests.unit;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCalculatedNotice;
import cl.duoc.xyzbank.interestsservice.interests.application.ports.InterestCalculatedPublisher;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.ApplyAnnualInterestUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InMemoryInterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.CreditInterestCommand;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestCreditResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.dto.InterestSummaryResponse;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The ApplyAnnualInterest use case")
class ApplyAnnualInterestUseCaseTest {

    /*
     * Cases:
     * 1. Applies annual interest using opening balance and configured rate
     * 2. Credits core-service with idempotency key interest-{accountId}-{year}
     * 3. Rejects null account ID
     * 4. Rejects blank account ID
     * 5. Rejects null year
     * 6. Rejects blank year
     * 7. Rejects non-numeric year
     * 8. With the Kafka flag on, stores a pending calculation and publishes without calling HTTP credit
     */

    private InMemoryCoreServicePort coreServicePort;
    private ApplyAnnualInterestUseCase useCase;

    @BeforeEach
    void setUp() {
        coreServicePort = new InMemoryCoreServicePort();
        useCase = new ApplyAnnualInterestUseCase(
                coreServicePort,
                new InterestRatePolicy(new BigDecimal("0.035")));
    }

    @Nested
    @DisplayName("when applying annual interest")
    class WhenApplyingAnnualInterest {

        @Test
        @DisplayName("applies annual interest using opening balance and configured rate")
        void appliesAnnualInterestUsingOpeningBalanceAndConfiguredRate() {
            coreServicePort.setBalance("account-123", new AccountBalanceResponse(
                    "account-123", new BigDecimal("1000.00"), "USD"));

            InterestSummaryResponse result = useCase.execute("account-123", "2025");

            assertEquals(new InterestSummaryResponse(
                    "account-123",
                    2025,
                    new BigDecimal("1000.00"),
                    new BigDecimal("1035.00"),
                    new BigDecimal("0.035"),
                    new BigDecimal("35.00"),
                    "USD"), result);
        }

        @Test
        @DisplayName("credits core-service with idempotency key interest-account-year")
        void creditsCoreServiceWithIdempotencyKeyInterestAccountYear() {
            coreServicePort.setBalance("account-123", new AccountBalanceResponse(
                    "account-123", new BigDecimal("1000.00"), "USD"));

            useCase.execute("account-123", "2025");

            CreditInterestCommand credit = coreServicePort.lastCredit();
            assertEquals("interest-account-123-2025", credit.idempotencyKey());
            assertEquals(new BigDecimal("35.00"), credit.amount());
            assertEquals(new BigDecimal("1000.00"), credit.openingBalance());
            assertEquals(new BigDecimal("1035.00"), credit.closingBalance());
            assertEquals(new BigDecimal("0.035"), credit.interestRate());
        }

        @Test
        @DisplayName("publishes a pending calculation and does not call the HTTP credit when the Kafka flag is on")
        void publishesAPendingCalculationAndDoesNotCallTheHttpCreditWhenTheKafkaFlagIsOn() {
            coreServicePort.setBalance("account-123", new AccountBalanceResponse(
                    "account-123", new BigDecimal("1000.00"), "USD"));
            InMemoryInterestCalculationRepository calculations = new InMemoryInterestCalculationRepository();
            RecordingPublisher publisher = new RecordingPublisher();
            ApplyAnnualInterestUseCase kafkaUseCase = new ApplyAnnualInterestUseCase(
                    coreServicePort,
                    new InterestRatePolicy(new BigDecimal("0.035")),
                    calculations,
                    publisher);

            InterestSummaryResponse result = kafkaUseCase.execute("account-123", "2025");

            assertEquals(new BigDecimal("35.00"), result.interestAmount());
            assertEquals(0, coreServicePort.creditCount());
            assertEquals(
                    InterestCalculationStatus.PENDING,
                    calculations.findByEventId("interest:account-123:2025").orElseThrow().status());
            assertEquals("interest:account-123:2025", publisher.notice.eventId());
            assertEquals("InterestCalculated", publisher.notice.eventType());
            assertEquals("account-123", publisher.notice.accountId());
            assertEquals(2025, publisher.notice.period());
            assertEquals(new BigDecimal("35.00"), publisher.notice.amount());
            assertEquals(new BigDecimal("1000.00"), publisher.notice.openingBalance());
            assertEquals(new BigDecimal("1035.00"), publisher.notice.closingBalance());
        }
    }

    @Nested
    @DisplayName("when validating input")
    class WhenValidatingInput {

        @Test
        @DisplayName("rejects null account ID")
        void rejectsNullAccountId() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute(null, "2025"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }

        @Test
        @DisplayName("rejects blank account ID")
        void rejectsBlankAccountId() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("  ", "2025"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }

        @Test
        @DisplayName("rejects null year")
        void rejectsNullYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", null));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }

        @Test
        @DisplayName("rejects blank year")
        void rejectsBlankYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", "  "));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }

        @Test
        @DisplayName("rejects non-numeric year")
        void rejectsNonNumericYear() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> useCase.execute("account-123", "abc"));
            assertEquals(DomainException.Type.VALIDATION, exception.getType());
        }
    }

    static class InMemoryCoreServicePort implements CoreServicePort {
        private final Map<String, AccountBalanceResponse> balances = new HashMap<>();
        private final List<CreditInterestCommand> credits = new ArrayList<>();

        void setBalance(String accountId, AccountBalanceResponse balance) {
            balances.put(accountId, balance);
        }

        CreditInterestCommand lastCredit() {
            return credits.get(credits.size() - 1);
        }

        int creditCount() {
            return credits.size();
        }

        @Override
        public InterestSummaryResponse fetchInterestSummary(String accountId, String year) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AccountBalanceResponse fetchAccountBalance(String accountId) {
            AccountBalanceResponse balance = balances.get(accountId);
            if (balance == null) {
                throw DomainException.notFound("Account not found");
            }
            return balance;
        }

        @Override
        public InterestCreditResponse creditInterest(CreditInterestCommand command) {
            credits.add(command);
            return new InterestCreditResponse(
                    "tx-1",
                    command.accountId(),
                    command.year(),
                    command.amount(),
                    command.currency(),
                    "2025-01-01T00:00:00Z",
                    command.closingBalance());
        }
    }

    static class RecordingPublisher implements InterestCalculatedPublisher {
        private InterestCalculatedNotice notice;

        @Override
        public void publish(InterestCalculatedNotice notice) {
            this.notice = notice;
        }
    }
}
