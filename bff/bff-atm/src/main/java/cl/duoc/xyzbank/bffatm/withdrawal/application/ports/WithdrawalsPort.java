package cl.duoc.xyzbank.bffatm.withdrawal.application.ports;

import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;

public interface WithdrawalsPort {

    WithdrawalResponse withdraw(String accountId, WithdrawalRequest request, String idempotencyKey);
}
