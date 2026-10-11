package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.OpenAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectoryUnavailableException;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The OpenAccount use case")
class OpenAccountUseCaseTest {

    /*
     * Cases:
     * 1. Opens an empty ACTIVE account for an existing customer
     * 2. Replaying the same key returns the same account without asking the directory again
     * 3. Rejects an unknown customer without opening anything
     * 4. Reports the directory as unavailable without opening anything
     * 5. Rejects a request without an idempotency key
     */

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final StubCustomerDirectory customers = new StubCustomerDirectory();
    private final OpenAccountUseCase useCase = new OpenAccountUseCase(accounts, customers);
    private final String customerId = Id.generate().getValue();

    @BeforeEach
    void knowTheCustomer() {
        customers.knows(customerId);
    }

    @Test
    @DisplayName("opens an empty ACTIVE account for an existing customer")
    void opensAnEmptyActiveAccountForAnExistingCustomer() {
        AccountResponse account = useCase.execute("open-1", new OpenAccountRequest(customerId, "USD", "Ahorro"));

        assertEquals(customerId, account.customerId());
        assertEquals(new BigDecimal("0.00"), account.balance());
        assertEquals("USD", account.currency());
        assertEquals("ACTIVE", account.status());
        assertEquals("Ahorro", account.alias());
        assertEquals(10, account.accountNumber().length());
        assertEquals(1, accounts.size());
    }

    @Test
    @DisplayName("replaying the same key returns the same account without asking the directory again")
    void replayingTheSameKeyReturnsTheSameAccountWithoutAskingTheDirectoryAgain() {
        AccountResponse first = useCase.execute("open-1", new OpenAccountRequest(customerId, "USD", null));

        AccountResponse retry = useCase.execute("open-1", new OpenAccountRequest(customerId, "USD", null));

        assertEquals(first.id(), retry.id());
        assertEquals(1, accounts.size());
        assertEquals(1, customers.lookups());
    }

    @Test
    @DisplayName("rejects an unknown customer without opening anything")
    void rejectsAnUnknownCustomerWithoutOpeningAnything() {
        OpenAccountRequest request = new OpenAccountRequest(Id.generate().getValue(), "USD", null);

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("open-2", request));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(0, accounts.size());
    }

    @Test
    @DisplayName("reports the directory as unavailable without opening anything")
    void reportsTheDirectoryAsUnavailableWithoutOpeningAnything() {
        customers.goDown();

        assertThrows(CustomerDirectoryUnavailableException.class,
                () -> useCase.execute("open-3", new OpenAccountRequest(customerId, "USD", null)));

        assertEquals(0, accounts.size());
    }

    @Test
    @DisplayName("rejects a request without an idempotency key")
    void rejectsARequestWithoutAnIdempotencyKey() {
        DomainException exception = assertThrows(DomainException.class,
                () -> useCase.execute(" ", new OpenAccountRequest(customerId, "USD", null)));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
        assertEquals(0, accounts.size());
    }
}
