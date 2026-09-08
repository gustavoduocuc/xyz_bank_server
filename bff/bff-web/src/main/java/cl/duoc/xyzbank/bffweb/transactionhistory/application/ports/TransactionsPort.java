package cl.duoc.xyzbank.bffweb.transactionhistory.application.ports;

import cl.duoc.xyzbank.bffweb.transactionhistory.application.dto.TransactionHistoryResponse;

public interface TransactionsPort {

    TransactionHistoryResponse fetchHistory(
            String accountId, String from, String to, String type, String cursor, Integer pageSize);
}
