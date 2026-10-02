package cl.duoc.xyzbank.bffatm.withdrawal.infrastructure.adapters;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.adapters.CoreServiceCalls;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalRequest;
import cl.duoc.xyzbank.bffatm.withdrawal.application.dto.WithdrawalResponse;
import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;
import org.springframework.http.MediaType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWithdrawalsAdapter implements WithdrawalsPort {

    private final RestClient coreServiceClient;

    public HttpWithdrawalsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    /**
     * Retried once at most: every attempt carries the terminal's own Idempotency-Key and the same
     * body, so core-service replays a committed withdrawal instead of debiting again
     * (bff-resilience spec; design.md Decision 5).
     */
    @Override
    @CircuitBreaker(name = "coreService")
    @Retry(name = "coreServiceWithdrawal")
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
