package cl.duoc.xyzbank.bffmobile.accountsummary.application.ports;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse.RecentTransaction;

import java.util.List;

public interface TransactionsPort {

    List<RecentTransaction> fetchLatestTransactions(String accountId);
}
