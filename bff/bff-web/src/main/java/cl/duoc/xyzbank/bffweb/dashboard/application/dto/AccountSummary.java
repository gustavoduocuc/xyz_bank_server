package cl.duoc.xyzbank.bffweb.dashboard.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountSummary(
        String id,
        String accountNumber,
        BigDecimal balance,
        String currency,
        List<RecentTransaction> transactions) {
}
