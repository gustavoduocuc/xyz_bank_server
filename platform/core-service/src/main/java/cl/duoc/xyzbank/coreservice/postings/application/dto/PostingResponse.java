package cl.duoc.xyzbank.coreservice.postings.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PostingResponse(String paymentId, List<PostedEntry> entries) {

    public record PostedEntry(
            String transactionId,
            String accountId,
            String direction,
            BigDecimal amount,
            String currency,
            BigDecimal balance) {
    }
}
