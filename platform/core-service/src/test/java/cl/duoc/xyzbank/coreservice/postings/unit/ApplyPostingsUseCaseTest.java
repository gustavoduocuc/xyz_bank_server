package cl.duoc.xyzbank.coreservice.postings.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingEntry;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingRequest;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingResponse;
import cl.duoc.xyzbank.coreservice.postings.application.usecases.ApplyPostingsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ApplyPostings use case")
class ApplyPostingsUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
    private static final String ACCOUNT_A = Id.generate().getValue();
    private static final String ACCOUNT_B = Id.generate().getValue();

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private final ApplyPostingsUseCase useCase = new ApplyPostingsUseCase(
            accountRepository, transactionRepository,
            new InMemoryPostingRepository(accountRepository, transactionRepository), CLOCK);

    /*
     * Cases:
     * 1. A transfer debits A and credits B, recording one transaction per entry
     * 2. A single credit (deposit) raises the balance
     * 3. A single debit (bill payment) lowers the balance
     * 4. Repeating the paymentId returns the same transaction ids without moving balances again
     * 5. Insufficient funds throws validation and records nothing
     * 6. A CLOSED account throws a conflict and records nothing
     * 7. An unknown account throws not found
     * 8. An invalid entry set (none, three, two debits, the same account twice, a non-positive amount,
     *    a currency mismatch, an unknown direction, a malformed paymentId) throws validation
     */

    @Test
    @DisplayName("debits A and credits B for a transfer, recording one transaction per entry")
    void debitsAAndCreditsBForATransfer() {
        anAccount(ACCOUNT_A, "500.00");
        anAccount(ACCOUNT_B, "0.00");
        String paymentId = UUID.randomUUID().toString();

        PostingResponse response = useCase.execute(new PostingRequest(paymentId,
                List.of(entry(ACCOUNT_A, "DEBIT", "100.00"), entry(ACCOUNT_B, "CREDIT", "100.00"))));

        assertEquals(paymentId, response.paymentId());
        assertEquals(2, response.entries().size());
        assertEquals(new BigDecimal("400.00"), response.entries().get(0).balance());
        assertEquals(new BigDecimal("100.00"), response.entries().get(1).balance());
        assertEquals(new BigDecimal("400.00"), balanceOf(ACCOUNT_A));
        assertEquals(new BigDecimal("100.00"), balanceOf(ACCOUNT_B));
        assertTrue(transactionRepository.findByIdempotencyKey("payment:" + paymentId + ":DEBIT").isPresent());
        assertTrue(transactionRepository.findByIdempotencyKey("payment:" + paymentId + ":CREDIT").isPresent());
    }

    @Test
    @DisplayName("raises the balance for a single credit")
    void raisesTheBalanceForASingleCredit() {
        anAccount(ACCOUNT_B, "10.00");

        useCase.execute(new PostingRequest(UUID.randomUUID().toString(), List.of(entry(ACCOUNT_B, "CREDIT", "40.00"))));

        assertEquals(new BigDecimal("50.00"), balanceOf(ACCOUNT_B));
    }

    @Test
    @DisplayName("lowers the balance for a single debit")
    void lowersTheBalanceForASingleDebit() {
        anAccount(ACCOUNT_A, "100.00");

        useCase.execute(new PostingRequest(UUID.randomUUID().toString(), List.of(entry(ACCOUNT_A, "DEBIT", "30.00"))));

        assertEquals(new BigDecimal("70.00"), balanceOf(ACCOUNT_A));
    }

    @Test
    @DisplayName("replays a repeated paymentId without moving balances again")
    void replaysARepeatedPaymentIdWithoutMovingBalancesAgain() {
        anAccount(ACCOUNT_A, "500.00");
        anAccount(ACCOUNT_B, "0.00");
        PostingRequest request = new PostingRequest(UUID.randomUUID().toString(),
                List.of(entry(ACCOUNT_A, "DEBIT", "100.00"), entry(ACCOUNT_B, "CREDIT", "100.00")));

        PostingResponse first = useCase.execute(request);
        PostingResponse second = useCase.execute(request);

        assertEquals(first, second);
        assertEquals(new BigDecimal("400.00"), balanceOf(ACCOUNT_A));
        assertEquals(new BigDecimal("100.00"), balanceOf(ACCOUNT_B));
    }

    @Test
    @DisplayName("throws validation for insufficient funds and records nothing")
    void throwsValidationForInsufficientFundsAndRecordsNothing() {
        anAccount(ACCOUNT_A, "50.00");
        anAccount(ACCOUNT_B, "0.00");
        String paymentId = UUID.randomUUID().toString();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(new PostingRequest(
                paymentId, List.of(entry(ACCOUNT_A, "DEBIT", "100.00"), entry(ACCOUNT_B, "CREDIT", "100.00")))));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertNothingRecordedFor(paymentId);
        assertEquals(new BigDecimal("0.00"), balanceOf(ACCOUNT_B));
    }

    @Test
    @DisplayName("throws a conflict for a CLOSED account and records nothing")
    void throwsAConflictForAClosedAccountAndRecordsNothing() {
        anAccount(ACCOUNT_A, "500.00");
        Account closed = Account.open(Id.create(ACCOUNT_B), AccountNumber.create("2222222222"), Id.generate(), "USD", null);
        closed.close(0, "close-1");
        accountRepository.save(closed);
        String paymentId = UUID.randomUUID().toString();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(new PostingRequest(
                paymentId, List.of(entry(ACCOUNT_A, "DEBIT", "100.00"), entry(ACCOUNT_B, "CREDIT", "100.00")))));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        assertNothingRecordedFor(paymentId);
    }

    @Test
    @DisplayName("throws not found for an unknown account")
    void throwsNotFoundForAnUnknownAccount() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(new PostingRequest(
                UUID.randomUUID().toString(), List.of(entry(Id.generate().getValue(), "CREDIT", "10.00")))));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("throws validation for an invalid entry set")
    void throwsValidationForAnInvalidEntrySet(String description, PostingRequest request) {
        anAccount(ACCOUNT_A, "500.00");
        anAccount(ACCOUNT_B, "500.00");

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(request));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(new BigDecimal("500.00"), balanceOf(ACCOUNT_A));
    }

    static Stream<Arguments> invalidRequests() {
        String paymentId = UUID.randomUUID().toString();
        return Stream.of(
                invalid("no entries", new PostingRequest(paymentId, List.of())),
                invalid("three entries", new PostingRequest(paymentId, List.of(
                        entry(ACCOUNT_A, "DEBIT", "1.00"), entry(ACCOUNT_B, "CREDIT", "1.00"),
                        entry(ACCOUNT_B, "CREDIT", "1.00")))),
                invalid("two debits", new PostingRequest(paymentId, List.of(
                        entry(ACCOUNT_A, "DEBIT", "1.00"), entry(ACCOUNT_B, "DEBIT", "1.00")))),
                invalid("the same account twice", new PostingRequest(paymentId, List.of(
                        entry(ACCOUNT_A, "DEBIT", "1.00"), entry(ACCOUNT_A, "CREDIT", "1.00")))),
                invalid("a non-positive amount", new PostingRequest(paymentId, List.of(
                        entry(ACCOUNT_A, "DEBIT", "0.00")))),
                invalid("a currency mismatch", new PostingRequest(paymentId, List.of(
                        new PostingEntry(ACCOUNT_A, "DEBIT", new BigDecimal("1.00"), "CLP")))),
                invalid("an unknown direction", new PostingRequest(paymentId, List.of(
                        entry(ACCOUNT_A, "REFUND", "1.00")))),
                invalid("a malformed paymentId", new PostingRequest("not-a-uuid", List.of(
                        entry(ACCOUNT_A, "DEBIT", "1.00")))));
    }

    private static Arguments invalid(String description, PostingRequest request) {
        return Arguments.of(description, request);
    }

    private static PostingEntry entry(String accountId, String direction, String amount) {
        return new PostingEntry(accountId, direction, new BigDecimal(amount), "USD");
    }

    private void anAccount(String accountId, String balance) {
        accountRepository.save(Account.create(Id.create(accountId), AccountNumber.create("1111111111"), Id.generate(),
                Money.create(new BigDecimal(balance), "USD")));
    }

    private BigDecimal balanceOf(String accountId) {
        return accountRepository.findById(Id.create(accountId)).orElseThrow().getBalance().getAmount();
    }

    private void assertNothingRecordedFor(String paymentId) {
        assertTrue(transactionRepository.findByIdempotencyKey("payment:" + paymentId + ":DEBIT").isEmpty());
        assertTrue(transactionRepository.findByIdempotencyKey("payment:" + paymentId + ":CREDIT").isEmpty());
    }
}
