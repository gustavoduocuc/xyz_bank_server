package cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.RecentTransaction;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.TransactionsPort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HttpTransactionsAdapter implements TransactionsPort {

    private final RestClient coreServiceClient;

    public HttpTransactionsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    public List<RecentTransaction> fetchLatestTransactions(String accountId, int pageSize) {
        TransactionPageWire page = coreServiceClient.get()
                .uri("/internal/accounts/{accountId}/transactions?pageSize={pageSize}", accountId, pageSize)
                .retrieve()
                .body(TransactionPageWire.class);
        return page.items().stream()
                .map(item -> new RecentTransaction(
                        item.id(), item.type(), item.amount(), item.currency(), item.occurredOn(), item.description()))
                .toList();
    }

    private record TransactionPageWire(List<TransactionSummaryWire> items, String nextCursor) {
    }

    private record TransactionSummaryWire(
            String id, String type, BigDecimal amount, String currency, String occurredOn, String description) {
    }
}
