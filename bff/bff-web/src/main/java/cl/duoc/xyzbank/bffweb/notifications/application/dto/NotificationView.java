package cl.duoc.xyzbank.bffweb.notifications.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** One entry of the customer's feed, as customers-service reports it (full web payload). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationView(
        String eventId,
        String kind,
        String occurredAt,
        String accountId,
        String type,
        BigDecimal amount,
        String currency) {
}
