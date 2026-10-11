package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ListAccountsForCustomer use case")
class ListAccountsForCustomerUseCaseTest {

    /*
     * Cases:
     * 1. Returns every account owned by the customer
     * 2. Returns an empty list when the customer owns no accounts
     * 3. Returns an empty list for an unknown customer id
     * 4. Excludes accounts owned by other customers
     */

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final ListAccountsForCustomerUseCase useCase =
            new ListAccountsForCustomerUseCase(accountRepository);

    @Test
    @DisplayName("returns every account owned by the customer")
    void returnsEveryAccountOwnedByTheCustomer() {
        Id customerId = Id.generate();
        accountRepository.save(anAccountFor(customerId, "1111111111"));
        accountRepository.save(anAccountFor(customerId, "2222222222"));

        List<AccountSummaryResponse> accounts = useCase.execute(customerId.getValue());

        assertEquals(2, accounts.size());
    }

    @Test
    @DisplayName("returns an empty list when the customer owns no accounts")
    void returnsAnEmptyListWhenTheCustomerOwnsNoAccounts() {
        Id customerId = Id.generate();

        List<AccountSummaryResponse> accounts = useCase.execute(customerId.getValue());

        assertTrue(accounts.isEmpty());
    }

    @Test
    @DisplayName("returns an empty list for an unknown customer id")
    void returnsAnEmptyListForAnUnknownCustomerId() {
        List<AccountSummaryResponse> accounts = useCase.execute(Id.generate().getValue());

        assertTrue(accounts.isEmpty());
    }

    @Test
    @DisplayName("excludes accounts owned by other customers")
    void excludesAccountsOwnedByOtherCustomers() {
        Id customerId = Id.generate();
        Id otherCustomerId = Id.generate();
        accountRepository.save(anAccountFor(customerId, "1111111111"));
        accountRepository.save(anAccountFor(otherCustomerId, "9999999999"));

        List<AccountSummaryResponse> accounts = useCase.execute(customerId.getValue());

        assertEquals(1, accounts.size());
        assertEquals("1111111111", accounts.get(0).accountNumber());
    }

    private Account anAccountFor(Id customerId, String accountNumber) {
        return Account.create(
                Id.generate(),
                AccountNumber.create(accountNumber),
                customerId,
                Money.create(new BigDecimal("100.00"), "USD"));
    }
}
