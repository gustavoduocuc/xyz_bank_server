package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountBalanceResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The GetAccountBalance use case")
class GetAccountBalanceUseCaseTest {

    /*
     * Cases:
     * 1. Returns the balance and currency of an existing account
     * 2. Throws not found for an unknown account id
     * 3. Throws validation for a malformed account id
     */

    @Test
    @DisplayName("returns the balance and currency of an existing account")
    void returnsTheBalanceAndCurrencyOfAnExistingAccount() {
        Id id = Id.generate();
        Account account = Account.create(
                id, AccountNumber.create("1234567890"), Id.generate(),
                Money.create(new BigDecimal("250.00"), "USD"));
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        repository.save(account);
        GetAccountBalanceUseCase useCase = new GetAccountBalanceUseCase(repository);

        AccountBalanceResponse response = useCase.execute(id.getValue());

        assertEquals(id.getValue(), response.accountId());
        assertEquals(new BigDecimal("250.00"), response.balance());
        assertEquals("USD", response.currency());
    }

    @Test
    @DisplayName("throws not found for an unknown account id")
    void throwsNotFoundForAnUnknownAccountId() {
        GetAccountBalanceUseCase useCase = new GetAccountBalanceUseCase(new InMemoryAccountRepository());

        DomainException exception = assertThrows(
                DomainException.class, () -> useCase.execute(Id.generate().getValue()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("throws validation for a malformed account id")
    void throwsValidationForAMalformedAccountId() {
        GetAccountBalanceUseCase useCase = new GetAccountBalanceUseCase(new InMemoryAccountRepository());

        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("   "));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}
