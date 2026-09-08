package cl.duoc.xyzbank.bffweb.dashboard.application.dto;

import java.math.BigDecimal;

public record RecentTransaction(
        String id,
        String type,
        BigDecimal amount,
        String currency,
        String occurredOn,
        String description) {
}
