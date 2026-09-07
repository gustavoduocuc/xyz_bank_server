package cl.duoc.xyzbank.coreservice.transactions.application.dto;

public record ListAccountTransactionsRequest(
        String accountId,
        String from,
        String to,
        String type,
        String cursor,
        Integer pageSize) {
}
