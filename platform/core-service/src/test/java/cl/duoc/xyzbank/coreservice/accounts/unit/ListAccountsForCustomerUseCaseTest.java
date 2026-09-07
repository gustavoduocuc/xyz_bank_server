package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Customer;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryCustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ListAccountsForCustomer use case")
class ListAccountsForCustomerUseCaseTest {

    /*
     * Cases:
     * 1. Returns every account owned by the customer
     * 2. Returns an empty list when the customer owns no accounts
     * 3. Throws not found for an unknown customer id
     * 4. Excludes accounts owned by other customers
     */

    private final InMemoryCustomerRepository customerRepository = new InMemoryCustomerRepository();
    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final ListAccountsForCustomerUseCase useCase =
            new ListAccountsForCustomerUseCase(customerRepository, accountRepository);

    @Test
    @DisplayName("returns every account owned by the customer")
    void returnsEveryAccountOwnedByTheCustomer() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe@xyzbank.cl"));
        accountRepository.save(anAccountFor(customerId, "1111111111"));
        accountRepository.save(anAccountFor(customerId, "2222222222"));

        List<AccountSummaryResponse> accounts = useCase.execute(customerId.getValue());

        assertEquals(2, accounts.size());
    }

    @Test
    @DisplayName("returns an empty list when the customer owns no accounts")
    void returnsAnEmptyListWhenTheCustomerOwnsNoAccounts() {
        Id customerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe@xyzbank.cl"));

        List<AccountSummaryResponse> accounts = useCase.execute(customerId.getValue());

        assertTrue(accounts.isEmpty());
    }

    @Test
    @DisplayName("throws not found for an unknown customer id")
    void throwsNotFoundForAnUnknownCustomerId() {
        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("excludes accounts owned by other customers")
    void excludesAccountsOwnedByOtherCustomers() {
        Id customerId = Id.generate();
        Id otherCustomerId = Id.generate();
        customerRepository.save(Customer.create(customerId, "Jane Doe", "jane.doe@xyzbank.cl"));
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
