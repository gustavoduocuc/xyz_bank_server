package cl.duoc.xyzbank.bffmobile.accountsummary.infrastructure.adapters;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class HttpAccountsAdapter implements AccountsPort {

    private final RestClient coreServiceClient;

    public HttpAccountsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    /** A read, so it may be retried (bff-resilience spec); the breaker sees every attempt. */
    @Override
    @CircuitBreaker(name = "coreService")
    @Retry(name = "coreServiceRead")
    public AccountBalance fetchBalance(String accountId) {
        BalanceWire wire = CoreServiceCalls.fetch(() -> coreServiceClient.get()
                .uri("/internal/accounts/{accountId}/balance", accountId)
                .retrieve()
                .body(BalanceWire.class));
        return new AccountBalance(wire.balance(), wire.currency());
    }

    private record BalanceWire(String accountId, BigDecimal balance, String currency) {
    }
}
