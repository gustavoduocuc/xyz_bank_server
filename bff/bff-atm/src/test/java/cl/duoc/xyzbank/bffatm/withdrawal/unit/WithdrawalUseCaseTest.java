package cl.duoc.xyzbank.bffatm.withdrawal.unit;

import cl.duoc.xyzbank.bffatm.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;
import cl.duoc.xyzbank.bffatm.withdrawal.application.usecases.WithdrawalUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("The Withdrawal use case")
class WithdrawalUseCaseTest {

    /*
     * Cases:
     * 1. Successful withdrawal forwards Idempotency-Key and body unchanged
     * 2. Missing Idempotency-Key is rejected before any core-service call
     * 3. Relays a core-service validation error unchanged
     * 4. Relays a core-service conflict unchanged
     * 5. Retry with the same key replays the original result
     */

    @Test
    @DisplayName("forwards the idempotency key and request body unchanged")
    void forwardsTheIdempotencyKeyAndRequestBodyUnchanged() {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), "USD");
        WithdrawalResponse expected = response("tx-1");
        RecordingWithdrawalsPort port = new RecordingWithdrawalsPort(expected);
        WithdrawalUseCase useCase = new WithdrawalUseCase(port);

        WithdrawalResponse actual = useCase.execute("account-1", request, "key-1");

        assertEquals(expected, actual);
        assertEquals("key-1", port.calls.get(0).idempotencyKey());
        assertEquals(request, port.calls.get(0).request());
    }

    @Test
    @DisplayName("rejects a missing idempotency key before calling core-service")
    void rejectsAMissingIdempotencyKeyBeforeCallingCoreService() {
        WithdrawalUseCase useCase = new WithdrawalUseCase((accountId, request, idempotencyKey) ->
                fail("withdrawals port should not be called"));

        assertThrows(
                RequestRejectedException.class,
                () -> useCase.execute("account-1", new WithdrawalRequest(new BigDecimal("40.00"), "USD"), null));
    }

    @Test
    @DisplayName("relays a core-service validation error unchanged")
    void relaysACoreServiceValidationErrorUnchanged() {
        WithdrawalUseCase useCase = new WithdrawalUseCase((accountId, request, idempotencyKey) -> {
            throw new CoreServiceCallException(422, "Insufficient funds");
        });

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> useCase.execute("account-1", new WithdrawalRequest(new BigDecimal("40.00"), "USD"), "key-1"));

        assertEquals(422, exception.getStatus());
        assertEquals("Insufficient funds", exception.getMessage());
    }

    @Test
    @DisplayName("relays a core-service conflict unchanged")
    void relaysACoreServiceConflictUnchanged() {
        WithdrawalUseCase useCase = new WithdrawalUseCase((accountId, request, idempotencyKey) -> {
            throw new CoreServiceCallException(409, "Conflict");
        });

        CoreServiceCallException exception = assertThrows(
                CoreServiceCallException.class,
                () -> useCase.execute("account-1", new WithdrawalRequest(new BigDecimal("40.00"), "USD"), "key-1"));

        assertEquals(409, exception.getStatus());
    }

    @Test
    @DisplayName("replays the original result when retried with the same idempotency key")
    void replaysTheOriginalResultWhenRetriedWithTheSameIdempotencyKey() {
        WithdrawalResponse original = response("tx-9");
        RecordingWithdrawalsPort port = new RecordingWithdrawalsPort(original, original);
        WithdrawalUseCase useCase = new WithdrawalUseCase(port);
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("40.00"), "USD");

        WithdrawalResponse first = useCase.execute("account-1", request, "key-1");
        WithdrawalResponse retry = useCase.execute("account-1", request, "key-1");

        assertEquals(original, first);
        assertEquals(original, retry);
        assertEquals("key-1", port.calls.get(0).idempotencyKey());
        assertEquals("key-1", port.calls.get(1).idempotencyKey());
    }

    private static WithdrawalResponse response(String transactionId) {
        return new WithdrawalResponse(
                transactionId, "account-1", new BigDecimal("40.00"), "USD", "2026-01-01", new BigDecimal("210.00"));
    }

    private record Call(String accountId, WithdrawalRequest request, String idempotencyKey) {
    }

    private static final class RecordingWithdrawalsPort implements WithdrawalsPort {
        private final List<WithdrawalResponse> results;
        private final List<Call> calls = new ArrayList<>();
        private int index;

        private RecordingWithdrawalsPort(WithdrawalResponse... results) {
            this.results = List.of(results);
        }

        @Override
        public WithdrawalResponse withdraw(String accountId, WithdrawalRequest request, String idempotencyKey) {
            calls.add(new Call(accountId, request, idempotencyKey));
            return results.get(index++);
        }
    }
}
