package cl.duoc.xyzbank.bffweb.transactionhistory.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse.TransactionItem;
import cl.duoc.xyzbank.bffweb.transactionhistory.application.ports.TransactionsPort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component("transactionHistoryHttpAdapter")
public class HttpTransactionsAdapter implements TransactionsPort {

    private final RestClient coreServiceClient;

    public HttpTransactionsAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    @Override
    public TransactionHistoryResponse fetchHistory(
            String accountId, String from, String to, String type, String cursor, Integer pageSize) {
        TransactionPageWire page = CoreServiceCalls.fetch(() -> coreServiceClient.get()
                .uri(uriBuilder -> historyUri(uriBuilder, accountId, from, to, type, cursor, pageSize))
                .retrieve()
                .body(TransactionPageWire.class));
        List<TransactionItem> items = page.items().stream()
                .map(item -> new TransactionItem(
                        item.id(), item.type(), item.amount(), item.currency(), item.occurredOn(), item.description()))
                .toList();
        return new TransactionHistoryResponse(items, page.nextCursor());
    }

    private URI historyUri(
            UriBuilder uriBuilder,
            String accountId,
            String from,
            String to,
            String type,
            String cursor,
            Integer pageSize) {
        uriBuilder.path("/internal/accounts/{accountId}/transactions");
        queryParam(uriBuilder, "from", from);
        queryParam(uriBuilder, "to", to);
        queryParam(uriBuilder, "type", type);
        queryParam(uriBuilder, "cursor", cursor);
        if (pageSize != null) {
            uriBuilder.queryParam("pageSize", pageSize);
        }
        return uriBuilder.build(accountId);
    }

    private void queryParam(UriBuilder uriBuilder, String name, String value) {
        Optional.ofNullable(value)
                .filter(item -> !item.isBlank())
                .ifPresent(item -> uriBuilder.queryParam(name, item));
    }

    private record TransactionPageWire(List<TransactionSummaryWire> items, String nextCursor) {
    }

    private record TransactionSummaryWire(
            String id, String type, BigDecimal amount, String currency, String occurredOn, String description) {
    }
}
