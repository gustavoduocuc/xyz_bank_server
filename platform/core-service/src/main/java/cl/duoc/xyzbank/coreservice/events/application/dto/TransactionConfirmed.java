package cl.duoc.xyzbank.coreservice.events.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionConfirmed(
        String eventId,
        String accountId,
        ConfirmedMovementType type,
        BigDecimal amount,
        String currency,
        LocalDate occurredAt) {
}
