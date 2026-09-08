package cl.duoc.xyzbank.bffweb.transactionhistory.unit;

import cl.duoc.xyzbank.bffweb.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse.TransactionItem;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.usecases.TransactionHistoryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("The Transaction History use case")
class TransactionHistoryUseCaseTest {

    /*
     * Cases:
     * 1. Filters by date range and type
     * 2. Cursor pagination continues across two pages with no overlap or gap
     * 3. Invalid date range is a validation failure
     * 4. Unrecognized transaction type is a validation failure
     * 5. Unknown account propagates not-found
     */

    @Test
    @DisplayName("filters history by date range and transaction type")
    void filtersHistoryByDateRangeAndType() {
        TransactionItem debit = item("tx-1", "DEBIT");
        RecordingTransactionsPort port = new RecordingTransactionsPort(
                new TransactionHistoryResponse(List.of(debit), null));
        TransactionHistoryUseCase useCase = new TransactionHistoryUseCase(port);

        TransactionHistoryResponse response =
                useCase.execute("account-1", "2026-01-01", "2026-01-31", "DEBIT", null, 20);

        assertEquals(List.of(debit), response.items());
        assertEquals("account-1", port.requests.get(0).accountId());
        assertEquals("2026-01-01", port.requests.get(0).from());
        assertEquals("2026-01-31", port.requests.get(0).to());
        assertEquals("DEBIT", port.requests.get(0).type());
    }

    @Test
    @DisplayName("continues cursor pagination across two pages with no overlap or gap")
    void continuesCursorPaginationAcrossTwoPages() {
        TransactionItem first = item("tx-1", "DEBIT");
        TransactionItem second = item("tx-2", "CREDIT");
        TransactionItem third = item("tx-3", "DEBIT");
        RecordingTransactionsPort port = new RecordingTransactionsPort(
                new TransactionHistoryResponse(List.of(first, second), "cursor-2"),
                new TransactionHistoryResponse(List.of(third), null));
        TransactionHistoryUseCase useCase = new TransactionHistoryUseCase(port);

        TransactionHistoryResponse pageOne = useCase.execute("account-1", null, null, null, null, 2);
        TransactionHistoryResponse pageTwo = useCase.execute("account-1", null, null, null, pageOne.nextCursor(), 2);

        assertEquals(List.of(first, second), pageOne.items());
        assertEquals(List.of(third), pageTwo.items());
        assertEquals("cursor-2", port.requests.get(1).cursor());
    }

    @Test
    @DisplayName("rejects a date range whose start is after its end")
    void rejectsADateRangeWhoseStartIsAfterItsEnd() {
        TransactionHistoryUseCase useCase = new TransactionHistoryUseCase(new PoisonTransactionsPort());

        assertThrows(
                RequestRejectedException.class,
                () -> useCase.execute("account-1", "2026-02-01", "2026-01-01", null, null, null));
    }

    @Test
    @DisplayName("rejects an unrecognized transaction type")
    void rejectsAnUnrecognizedTransactionType() {
        TransactionHistoryUseCase useCase = new TransactionHistoryUseCase(new PoisonTransactionsPort());

        assertThrows(
                RequestRejectedException.class,
                () -> useCase.execute("account-1", null, null, "TRANSFER", null, null));
    }

    @Test
    @DisplayName("propagates not-found for an unknown account")
    void propagatesNotFoundForAnUnknownAccount() {
        TransactionsPort port = (accountId, from, to, type, cursor, pageSize) -> {
            throw new CoreServiceCallException(404, "Account " + accountId + " not found");
        };
        TransactionHistoryUseCase useCase = new TransactionHistoryUseCase(port);

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> useCase.execute("unknown", null, null, null, null, null));

        assertEquals(404, exception.getStatus());
    }

    private static TransactionItem item(String id, String type) {
        return new TransactionItem(id, type, new BigDecimal("10.00"), "USD", "2026-01-01", null);
    }

    private record HistoryRequest(
            String accountId, String from, String to, String type, String cursor, Integer pageSize) {
    }

    private static final class RecordingTransactionsPort implements TransactionsPort {
        private final List<TransactionHistoryResponse> pages;
        private final List<HistoryRequest> requests = new ArrayList<>();
        private int index;

        private RecordingTransactionsPort(TransactionHistoryResponse... pages) {
            this.pages = List.of(pages);
        }

        @Override
        public TransactionHistoryResponse fetchHistory(
                String accountId, String from, String to, String type, String cursor, Integer pageSize) {
            requests.add(new HistoryRequest(accountId, from, to, type, cursor, pageSize));
            return pages.get(index++);
        }
    }

    private static final class PoisonTransactionsPort implements TransactionsPort {
        @Override
        public TransactionHistoryResponse fetchHistory(
                String accountId, String from, String to, String type, String cursor, Integer pageSize) {
            return fail("transactions port should not be called");
        }
    }
}
