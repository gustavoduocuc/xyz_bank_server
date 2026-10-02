package cl.duoc.xyzbank.bffmobile.accountsummary.infrastructure.adapters;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse.RecentTransaction;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HttpTransactionsAdapter implements TransactionsPort {

    private static final int FIXED_PAGE_SIZE = 5;

    private final RestClient coreServiceClient;

    public HttpTransactionsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    /** A read, so it may be retried (bff-resilience spec); the breaker sees every attempt. */
    @Override
    @CircuitBreaker(name = "coreService")
    @Retry(name = "coreServiceRead")
    public List<RecentTransaction> fetchLatestTransactions(String accountId) {
        TransactionPageWire page = CoreServiceCalls.fetch(() -> coreServiceClient.get()
                .uri("/internal/accounts/{accountId}/transactions?pageSize={pageSize}", accountId, FIXED_PAGE_SIZE)
                .retrieve()
                .body(TransactionPageWire.class));
        return page.items().stream()
                .map(item -> new RecentTransaction(item.id(), item.type(), item.amount(), item.occurredOn()))
                .toList();
    }

    private record TransactionPageWire(List<TransactionSummaryWire> items, String nextCursor) {
    }

    private record TransactionSummaryWire(
            String id, String type, BigDecimal amount, String currency, String occurredOn, String description) {
    }
}
