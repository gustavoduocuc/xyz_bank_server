package cl.duoc.xyzbank.coreservice.transactions.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import cl.duoc.xyzbank.coredomain.transactions.unit.InMemoryTransactionRepository;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.ListAccountTransactionsRequest;
import cl.duoc.xyzbank.coreservice.transactions.application.dto.TransactionPageResponse;
import cl.duoc.xyzbank.coreservice.transactions.application.usecases.ListAccountTransactionsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ListAccountTransactions use case")
class ListAccountTransactionsUseCaseTest {

    /*
     * Cases:
     * 1. Returns a first page without a cursor
     * 2. Following a returned cursor returns the next page with no repeats or gaps
     * 3. Returns an empty page for an account with no transactions
     * 4. Throws not found for an unknown account
     * 5. Filters by date range
     * 6. Filters by type
     * 7. Throws validation for an inverted date range
     * 8. Throws validation for an unrecognized type
     * 9. Throws validation for a non-positive page size
     * 10. Throws validation for a malformed cursor
     * 11. Clamps an oversized page size instead of rejecting it
     */

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
    private final ListAccountTransactionsUseCase useCase =
            new ListAccountTransactionsUseCase(accountRepository, transactionRepository);

    @Test
    @DisplayName("returns a first page without a cursor")
    void returnsAFirstPageWithoutACursor() {
        Id accountId = anExistingAccount();
        saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, 5));
        saveTransaction(accountId, TransactionType.CREDIT, LocalDate.of(2026, 1, 10));

        TransactionPageResponse response = useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, null, null));

        assertEquals(2, response.items().size());
        assertEquals("2026-01-10", response.items().get(0).occurredOn());
        assertNull(response.nextCursor());
    }

    @Test
    @DisplayName("following a returned cursor returns the next page with no repeats or gaps")
    void followingAReturnedCursorReturnsTheNextPageWithNoRepeatsOrGaps() {
        Id accountId = anExistingAccount();
        for (int day = 1; day <= 5; day++) {
            saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, day));
        }

        TransactionPageResponse firstPage = useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, null, 3));
        assertEquals(3, firstPage.items().size());
        assertTrue(firstPage.nextCursor() != null);

        TransactionPageResponse secondPage = useCase.execute(new ListAccountTransactionsRequest(
                accountId.getValue(), null, null, null, firstPage.nextCursor(), 3));

        assertEquals(2, secondPage.items().size());
        assertNull(secondPage.nextCursor());
        long distinctDays = java.util.stream.Stream.concat(
                        firstPage.items().stream(), secondPage.items().stream())
                .map(item -> item.occurredOn())
                .distinct()
                .count();
        assertEquals(5, distinctDays);
    }

    @Test
    @DisplayName("returns an empty page for an account with no transactions")
    void returnsAnEmptyPageForAnAccountWithNoTransactions() {
        Id accountId = anExistingAccount();

        TransactionPageResponse response = useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, null, null));

        assertTrue(response.items().isEmpty());
        assertNull(response.nextCursor());
    }

    @Test
    @DisplayName("throws not found for an unknown account")
    void throwsNotFoundForAnUnknownAccount() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new ListAccountTransactionsRequest(Id.generate().getValue(), null, null, null, null, null)));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("filters by date range")
    void filtersByDateRange() {
        Id accountId = anExistingAccount();
        saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, 1));
        saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, 20));

        TransactionPageResponse response = useCase.execute(new ListAccountTransactionsRequest(
                accountId.getValue(), "2026-01-15", "2026-01-31", null, null, null));

        assertEquals(1, response.items().size());
        assertEquals("2026-01-20", response.items().get(0).occurredOn());
    }

    @Test
    @DisplayName("filters by type")
    void filtersByType() {
        Id accountId = anExistingAccount();
        saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, 1));
        saveTransaction(accountId, TransactionType.CREDIT, LocalDate.of(2026, 1, 2));

        TransactionPageResponse response = useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, "CREDIT", null, null));

        assertEquals(1, response.items().size());
        assertEquals("CREDIT", response.items().get(0).type());
    }

    @Test
    @DisplayName("throws validation for an inverted date range")
    void throwsValidationForAnInvertedDateRange() {
        Id accountId = anExistingAccount();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), "2026-02-01", "2026-01-01", null, null, null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for an unrecognized type")
    void throwsValidationForAnUnrecognizedType() {
        Id accountId = anExistingAccount();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, "REFUND", null, null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a non-positive page size")
    void throwsValidationForANonPositivePageSize() {
        Id accountId = anExistingAccount();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, null, 0)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a malformed cursor")
    void throwsValidationForAMalformedCursor() {
        Id accountId = anExistingAccount();

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, "not-a-cursor", null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("clamps an oversized page size instead of rejecting it")
    void clampsAnOversizedPageSizeInsteadOfRejectingIt() {
        Id accountId = anExistingAccount();
        for (int day = 1; day <= 5; day++) {
            saveTransaction(accountId, TransactionType.DEBIT, LocalDate.of(2026, 1, day));
        }

        TransactionPageResponse response = useCase.execute(
                new ListAccountTransactionsRequest(accountId.getValue(), null, null, null, null, 1000));

        assertEquals(5, response.items().size());
    }

    private Id anExistingAccount() {
        Id accountId = Id.generate();
        accountRepository.save(Account.create(
                accountId, AccountNumber.create("1234567890"), Id.generate(),
                Money.create(new BigDecimal("100.00"), "USD")));
        return accountId;
    }

    private void saveTransaction(Id accountId, TransactionType type, LocalDate occurredOn) {
        transactionRepository.save(Transaction.create(
                Id.generate(), accountId, type, Money.create(new BigDecimal("10.00"), "USD"), occurredOn, null));
    }
}
