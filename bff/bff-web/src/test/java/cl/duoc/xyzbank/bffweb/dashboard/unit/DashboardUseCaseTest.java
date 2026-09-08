package cl.duoc.xyzbank.bffweb.dashboard.unit;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.AccountSummary;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.DashboardResponse;
import cl.duoc.xyzbank.bffweb.dashboard.application.dto.RecentTransaction;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffweb.dashboard.application.usecases.DashboardUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The Dashboard use case")
class DashboardUseCaseTest {

    /*
     * Cases:
     * 1. Successful aggregate: profile, every account with balance, latest transactions per account
     */

    @Test
    @DisplayName("aggregates profile, accounts with balances, and each account's latest transactions")
    void aggregatesProfileAccountsAndLatestTransactions() {
        CustomerProfile profile = new CustomerProfile("customer-1", "Ana Perez", "ana@example.com");
        AccountBalance accountA = new AccountBalance("account-1", "1000000001", new BigDecimal("500.00"), "USD");
        AccountBalance accountB = new AccountBalance("account-2", "1000000002", new BigDecimal("900.00"), "USD");
        RecentTransaction txA = new RecentTransaction("tx-1", "DEBIT", new BigDecimal("50.00"), "USD", "2026-01-01", null);
        RecentTransaction txB = new RecentTransaction("tx-2", "CREDIT", new BigDecimal("200.00"), "USD", "2026-01-02", null);

        DashboardUseCase useCase = new DashboardUseCase(
                new StubCustomerProfilePort(profile),
                new StubAccountsPort(List.of(accountA, accountB)),
                new StubTransactionsPort(Map.of(
                        "account-1", List.of(txA),
                        "account-2", List.of(txB))));

        DashboardResponse response = useCase.execute("customer-1");

        assertEquals(profile, response.profile());
        assertEquals(
                List.of(
                        new AccountSummary("account-1", "1000000001", new BigDecimal("500.00"), "USD", List.of(txA)),
                        new AccountSummary("account-2", "1000000002", new BigDecimal("900.00"), "USD", List.of(txB))),
                response.accounts());
    }

    private record StubCustomerProfilePort(CustomerProfile profile) implements CustomerProfilePort {
        @Override
        public CustomerProfile fetchProfile(String customerId) {
            return profile;
        }
    }

    private record StubAccountsPort(List<AccountBalance> accounts) implements AccountsPort {
        @Override
        public List<AccountBalance> fetchAccountsForCustomer(String customerId) {
            return accounts;
        }
    }

    private record StubTransactionsPort(Map<String, List<RecentTransaction>> transactionsByAccountId)
            implements TransactionsPort {
        @Override
        public List<RecentTransaction> fetchLatestTransactions(String accountId, int pageSize) {
            return transactionsByAccountId.getOrDefault(accountId, List.of());
        }
    }
}
