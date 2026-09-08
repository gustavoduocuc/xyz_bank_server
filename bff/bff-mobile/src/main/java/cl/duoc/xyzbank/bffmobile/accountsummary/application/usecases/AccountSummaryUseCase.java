package cl.duoc.xyzbank.bffmobile.accountsummary.application.usecases;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.TransactionsPort;

public class AccountSummaryUseCase {

    private final AccountsPort accountsPort;
    private final TransactionsPort transactionsPort;

    public AccountSummaryUseCase(AccountsPort accountsPort, TransactionsPort transactionsPort) {
        this.accountsPort = accountsPort;
        this.transactionsPort = transactionsPort;
    }

    public AccountSummaryResponse execute(String accountId) {
        AccountBalance balance = accountsPort.fetchBalance(accountId);
        return new AccountSummaryResponse(
                balance.balance(), balance.currency(), transactionsPort.fetchLatestTransactions(accountId));
    }
}
