package cl.duoc.xyzbank.bffmobile.accountsummary.unit;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountBalance;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.dto.AccountSummaryResponse.RecentTransaction;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.usecases.AccountSummaryUseCase;
import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The Account Summary use case")
class AccountSummaryUseCaseTest {

    /*
     * Cases:
     * 1. Balance plus last 5 transactions, flattened
     * 2. Account with fewer than 5 transactions returns all of them
     * 3. Unknown account is not-found
     */

    @Test
    @DisplayName("returns balance and the last five transactions flattened")
    void returnsBalanceAndTheLastFiveTransactionsFlattened() {
        List<RecentTransaction> lastFive = IntStream.rangeClosed(1, 5)
                .mapToObj(index -> new RecentTransaction("tx-" + index, "DEBIT", new BigDecimal("10.00"), "2026-01-0" + index))
                .toList();
        AccountSummaryUseCase useCase = new AccountSummaryUseCase(
                accountId -> new AccountBalance(new BigDecimal("500.00"), "USD"),
                accountId -> lastFive);

        AccountSummaryResponse response = useCase.execute("account-1");

        assertEquals(new BigDecimal("500.00"), response.balance());
        assertEquals("USD", response.currency());
        assertEquals(5, response.transactions().size());
        assertEquals(lastFive, response.transactions());
    }

    @Test
    @DisplayName("returns all transactions when the account has fewer than five")
    void returnsAllTransactionsWhenFewerThanFive() {
        List<RecentTransaction> two = List.of(
                new RecentTransaction("tx-1", "DEBIT", new BigDecimal("10.00"), "2026-01-01"),
                new RecentTransaction("tx-2", "CREDIT", new BigDecimal("20.00"), "2026-01-02"));
        AccountSummaryUseCase useCase = new AccountSummaryUseCase(
                accountId -> new AccountBalance(new BigDecimal("80.00"), "USD"),
                accountId -> two);

        AccountSummaryResponse response = useCase.execute("account-1");

        assertEquals(two, response.transactions());
    }

    @Test
    @DisplayName("propagates not-found for an unknown account")
    void propagatesNotFoundForAnUnknownAccount() {
        AccountsPort accountsPort = accountId -> {
            throw new CoreServiceCallException(404, "Account not found");
        };
        AccountSummaryUseCase useCase = new AccountSummaryUseCase(accountsPort, accountId -> List.of());

        CoreServiceCallException exception =
                assertThrows(CoreServiceCallException.class, () -> useCase.execute("unknown"));

        assertEquals(404, exception.getStatus());
    }
}
