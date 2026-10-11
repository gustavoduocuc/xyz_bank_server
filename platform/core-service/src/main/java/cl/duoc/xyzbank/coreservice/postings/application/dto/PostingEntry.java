package cl.duoc.xyzbank.coreservice.postings.application.dto;

import java.math.BigDecimal;

public record PostingEntry(String accountId, String direction, BigDecimal amount, String currency) {
}
