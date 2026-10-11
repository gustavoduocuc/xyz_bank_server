package cl.duoc.xyzbank.coreservice.accounts.application.dto;

public record OpenAccountRequest(String customerId, String currency, String alias) {
}
