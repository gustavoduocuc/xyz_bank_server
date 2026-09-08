package cl.duoc.xyzbank.bffweb.transactionhistory.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionHistoryResponse(List<TransactionItem> items, String nextCursor) {

    public record TransactionItem(
            String id,
            String type,
            BigDecimal amount,
            String currency,
            String occurredOn,
            String description) {
    }
}
