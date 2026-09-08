package cl.duoc.xyzbank.bffweb.dashboard.application.usecases;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountSummary;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.DashboardResponse;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.TransactionsPort;

import java.util.List;

public class DashboardUseCase {

    private static final int LATEST_TRANSACTIONS_PAGE_SIZE = 5;

    private final CustomerProfilePort customerProfilePort;
    private final AccountsPort accountsPort;
    private final TransactionsPort transactionsPort;

    public DashboardUseCase(
            CustomerProfilePort customerProfilePort,
            AccountsPort accountsPort,
            TransactionsPort transactionsPort) {
        this.customerProfilePort = customerProfilePort;
        this.accountsPort = accountsPort;
        this.transactionsPort = transactionsPort;
    }

    public DashboardResponse execute(String customerId) {
        CustomerProfile profile = customerProfilePort.fetchProfile(customerId);
        List<AccountBalance> accounts = accountsPort.fetchAccountsForCustomer(customerId);
        List<AccountSummary> accountSummaries = accounts.stream()
                .map(this::toAccountSummary)
                .toList();
        return new DashboardResponse(profile, accountSummaries);
    }

    private AccountSummary toAccountSummary(AccountBalance account) {
        return new AccountSummary(
                account.id(),
                account.accountNumber(),
                account.balance(),
                account.currency(),
                transactionsPort.fetchLatestTransactions(account.id(), LATEST_TRANSACTIONS_PAGE_SIZE));
    }
}
