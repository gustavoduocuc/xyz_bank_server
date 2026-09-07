package cl.duoc.xyzbank.coreservice.withdrawals.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawRequest;
import cl.duoc.xyzbank.coreservice.withdrawals.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.coreservice.withdrawals.application.usecases.WithdrawAccountUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The WithdrawAccount use case")
class WithdrawAccountUseCaseTest {

    private static final BigDecimal DAILY_LIMIT = new BigDecimal("1000.00");
    private static final Clock CLOCK_DAY_1 =
            Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock CLOCK_DAY_2 =
            Clock.fixed(Instant.parse("2026-01-02T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private final InMemoryWithdrawalRepository withdrawalRepository =
            new InMemoryWithdrawalRepository(accountRepository, transactionRepository);

    /*
     * Cases:
     * 1. Successful withdrawal reduces the balance and returns the new balance and a transaction id
     * 2. Throws not found for an unknown account
     * 3. Throws validation for a missing idempotency key
     * 4. Throws validation for a non-positive amount
     * 5. Throws validation for a currency mismatch
     * 6. Throws validation for insufficient funds
     * 7. Throws validation for an exceeded daily limit
     * 8. Repeated idempotency key with the same account and amount replays the original result
     * 9. Repeated idempotency key with a different amount throws a conflict
     * 10. The daily limit resets on a new day
     */

    @Test
    @DisplayName("reduces the balance and returns the new balance and a transaction id")
    void reducesTheBalanceAndReturnsTheNewBalanceAndATransactionId() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        WithdrawalResponse response = useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("100.00"), "USD", "key-1"));

        assertEquals(new BigDecimal("400.00"), response.newBalance());
        assertEquals(accountId.getValue(), response.accountId());
        assertEquals(new BigDecimal("100.00"), response.amount());
    }

    @Test
    @DisplayName("throws not found for an unknown account")
    void throwsNotFoundForAnUnknownAccount() {
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(Id.generate().getValue(), new BigDecimal("10.00"), "USD", "key-2")));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a missing idempotency key")
    void throwsValidationForAMissingIdempotencyKey() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("10.00"), "USD", null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a non-positive amount")
    void throwsValidationForANonPositiveAmount() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("0.00"), "USD", "key-3")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a currency mismatch")
    void throwsValidationForACurrencyMismatch() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("10.00"), "CLP", "key-4")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for insufficient funds")
    void throwsValidationForInsufficientFunds() {
        Id accountId = anExistingAccount("50.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("100.00"), "USD", "key-5")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for an exceeded daily limit")
    void throwsValidationForAnExceededDailyLimit() {
        Id accountId = anExistingAccount("5000.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("1500.00"), "USD", "key-6")));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("replays the original result for a repeated idempotency key with the same account and amount")
    void replaysTheOriginalResultForARepeatedIdempotencyKeyWithTheSameAccountAndAmount() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);
        WithdrawalResponse first = useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("100.00"), "USD", "key-7"));

        WithdrawalResponse replay = useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("100.00"), "USD", "key-7"));

        assertEquals(first.transactionId(), replay.transactionId());
        assertEquals(new BigDecimal("400.00"), replay.newBalance());
    }

    @Test
    @DisplayName("throws a conflict for a repeated idempotency key with a different amount")
    void throwsAConflictForARepeatedIdempotencyKeyWithADifferentAmount() {
        Id accountId = anExistingAccount("500.00");
        WithdrawAccountUseCase useCase = useCase(CLOCK_DAY_1);
        useCase.execute(new WithdrawRequest(accountId.getValue(), new BigDecimal("100.00"), "USD", "key-8"));

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("50.00"), "USD", "key-8")));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    @Test
    @DisplayName("resets the daily limit on a new day")
    void resetsTheDailyLimitOnANewDay() {
        Id accountId = anExistingAccount("5000.00");
        WithdrawAccountUseCase useCaseDay1 = useCase(CLOCK_DAY_1);
        useCaseDay1.execute(new WithdrawRequest(accountId.getValue(), new BigDecimal("1000.00"), "USD", "key-9"));
        WithdrawAccountUseCase useCaseDay2 = useCase(CLOCK_DAY_2);

        WithdrawalResponse response = useCaseDay2.execute(
                new WithdrawRequest(accountId.getValue(), new BigDecimal("900.00"), "USD", "key-10"));

        assertEquals(new BigDecimal("3100.00"), response.newBalance());
    }

    private Id anExistingAccount(String balance) {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create("1234567890"), Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
        return accountId;
    }

    private WithdrawAccountUseCase useCase(Clock clock) {
        return new WithdrawAccountUseCase(accountRepository, transactionRepository, withdrawalRepository, DAILY_LIMIT, clock);
    }
}
