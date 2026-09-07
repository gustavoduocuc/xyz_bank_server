package cl.duoc.xyzbank.coreservice.transactions.application.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionSummaryResponse> items,
        String nextCursor) {
}
