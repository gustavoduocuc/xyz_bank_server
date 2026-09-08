package cl.duoc.xyzbank.bffmobile.accountsummary.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountSummaryResponse(BigDecimal balance, String currency, List<RecentTransaction> transactions) {

    public record RecentTransaction(String id, String type, BigDecimal amount, String occurredOn) {
    }
}
