package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.AccountNumber;
import cl.duoc.xyzbank.coredomain.accounts.unit.InMemoryAccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateAccountRequest;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateAccountUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The UpdateAccount use case")
class UpdateAccountUseCaseTest {

    /*
     * Cases:
     * 1. Applies the alias and the account's own daily limit
     * 2. Repeating the last key returns the account without checking the version again
     * 3. Rejects a stale version
     * 4. Fails with not-found for an unknown account
     * 5. Rejects a request without a version or an idempotency key
     */

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final UpdateAccountUseCase useCase = new UpdateAccountUseCase(accounts);
    private final Id accountId = Id.generate();

    @BeforeEach
    void openAccount() {
        accounts.save(Account.open(accountId, AccountNumber.create("1234567890"), Id.generate(), "USD", null));
    }

    @Test
    @DisplayName("applies the alias and the account's own daily limit")
    void appliesTheAliasAndTheAccountsOwnDailyLimit() {
        AccountResponse account = useCase.execute(
                accountId.getValue(), "upd-1", new UpdateAccountRequest("Viajes", new BigDecimal("800.00"), 0L));

        assertEquals("Viajes", account.alias());
        assertEquals(new BigDecimal("800.00"), account.dailyWithdrawalLimit());
    }

    @Test
    @DisplayName("repeating the last key returns the account without checking the version again")
    void repeatingTheLastKeyReturnsTheAccountWithoutCheckingTheVersionAgain() {
        useCase.execute(accountId.getValue(), "upd-1", new UpdateAccountRequest("Viajes", null, 0L));

        AccountResponse retry = useCase.execute(
                accountId.getValue(), "upd-1", new UpdateAccountRequest("Viajes", null, 99L));

        assertEquals("Viajes", retry.alias());
    }

    @Test
    @DisplayName("rejects a stale version")
    void rejectsAStaleVersion() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                accountId.getValue(), "upd-2", new UpdateAccountRequest("Viajes", null, 5L)));

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    @Test
    @DisplayName("fails with not-found for an unknown account")
    void failsWithNotFoundForAnUnknownAccount() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute(
                Id.generate().getValue(), "upd-3", new UpdateAccountRequest("Viajes", null, 0L)));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("rejects a request without a version or an idempotency key")
    void rejectsARequestWithoutAVersionOrAnIdempotencyKey() {
        DomainException noVersion = assertThrows(DomainException.class, () -> useCase.execute(
                accountId.getValue(), "upd-4", new UpdateAccountRequest("Viajes", null, null)));
        DomainException noKey = assertThrows(DomainException.class, () -> useCase.execute(
                accountId.getValue(), null, new UpdateAccountRequest("Viajes", null, 0L)));

        assertEquals(DomainException.Type.VALIDATION, noVersion.getType());
        assertEquals(DomainException.Type.VALIDATION, noKey.getType());
    }
}
