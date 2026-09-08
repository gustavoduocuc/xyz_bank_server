package cl.duoc.xyzbank.bffweb.dashboard.application.ports;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.RecentTransaction;

import java.util.List;

public interface TransactionsPort {

    List<RecentTransaction> fetchLatestTransactions(String accountId, int pageSize);
}
