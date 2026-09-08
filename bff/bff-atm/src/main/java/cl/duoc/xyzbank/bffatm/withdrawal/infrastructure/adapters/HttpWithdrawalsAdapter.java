package cl.duoc.xyzbank.bffatm.withdrawal.infrastructure.adapters;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCalls;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWithdrawalsAdapter implements WithdrawalsPort {

    private final RestClient coreServiceClient;

    public HttpWithdrawalsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    public WithdrawalResponse withdraw(String accountId, WithdrawalRequest request, String idempotencyKey) {
        return CoreServiceCalls.fetch(() -> coreServiceClient.post()
                .uri("/internal/accounts/{accountId}/withdrawals", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(WithdrawalResponse.class));
    }
}
