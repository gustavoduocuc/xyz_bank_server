package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CloseAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The CloseAccount use case")
class CloseAccountUseCaseTest {

    /*
     * Cases:
     * 1. Closes an account with a zero balance
     * 2. Rejects closing an account with funds and leaves it ACTIVE
     * 3. Repeating the closure key returns the closed account
     */

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final CloseAccountUseCase useCase = new CloseAccountUseCase(accounts);

    @Test
    @DisplayName("closes an account with a zero balance")
    void closesAnAccountWithAZeroBalance() {
        Id accountId = openAccount("0.00");

        AccountResponse account = useCase.execute(accountId.getValue(), "close-1", new CloseAccountRequest(0L));

        assertEquals("CLOSED", account.status());
    }

    @Test
    @DisplayName("rejects closing an account with funds and leaves it ACTIVE")
    void rejectsClosingAnAccountWithFundsAndLeavesItActive() {
        Id accountId = openAccount("10.00");

        DomainException exception = assertThrows(DomainException.class,
                () -> useCase.execute(accountId.getValue(), "close-1", new CloseAccountRequest(0L)));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
        assertEquals("ACTIVE", accounts.findById(accountId).orElseThrow().getStatus().name());
    }

    @Test
    @DisplayName("repeating the closure key returns the closed account")
    void repeatingTheClosureKeyReturnsTheClosedAccount() {
        Id accountId = openAccount("0.00");
        useCase.execute(accountId.getValue(), "close-1", new CloseAccountRequest(0L));

        AccountResponse retry = useCase.execute(accountId.getValue(), "close-1", new CloseAccountRequest(0L));

        assertEquals("CLOSED", retry.status());
    }

    private Id openAccount(String balance) {
        Id accountId = Id.generate();
        Account account = Account.open(accountId, AccountNumber.create("1234567890"), Id.generate(), "USD", null);
        if (new BigDecimal(balance).signum() > 0) {
            account.credit(Money.create(new BigDecimal(balance), "USD"));
        }
        accounts.save(account);
        return accountId;
    }
}
