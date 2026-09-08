package cl.duoc.xyzbank.bffatm.withdrawal.application.usecases;

import cl.duoc.xyzbank.bffatm.shared.application.RequestRejectedException;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;

public class WithdrawalUseCase {

    private final WithdrawalsPort withdrawalsPort;

    public WithdrawalUseCase(WithdrawalsPort withdrawalsPort) {
        this.withdrawalsPort = withdrawalsPort;
    }

    public WithdrawalResponse execute(String accountId, WithdrawalRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw RequestRejectedException.validation("Idempotency-Key is required");
        }
        return withdrawalsPort.withdraw(accountId, request, idempotencyKey);
    }
}
