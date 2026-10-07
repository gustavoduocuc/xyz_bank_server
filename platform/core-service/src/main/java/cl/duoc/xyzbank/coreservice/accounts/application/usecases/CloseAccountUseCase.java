package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.CloseAccountRequest;

/** Repeating the closure's key returns the closed account. */
public class CloseAccountUseCase {

    private final AccountRepository accountRepository;

    public CloseAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse execute(String accountId, String idempotencyKey, CloseAccountRequest request) {
        AccountCommands.requireIdempotencyKey(idempotencyKey);
        long version = AccountCommands.requireVersion(request.version());
        Account account = AccountCommands.existing(accountRepository, accountId);
        if (account.isLastCommand(idempotencyKey)) {
            return AccountResponse.from(account);
        }
        account.close(version, idempotencyKey);
        return AccountResponse.from(AccountCommands.saved(accountRepository, account));
    }
}
