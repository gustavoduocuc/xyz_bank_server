package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.valueobjects.Money;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountResponse;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.UpdateAccountRequest;

/** Repeating the key of the last change applied returns the account as it is. */
public class UpdateAccountUseCase {

    private final AccountRepository accountRepository;

    public UpdateAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse execute(String accountId, String idempotencyKey, UpdateAccountRequest request) {
        AccountCommands.requireIdempotencyKey(idempotencyKey);
        long version = AccountCommands.requireVersion(request.version());
        Account account = AccountCommands.existing(accountRepository, accountId);
        if (account.isLastCommand(idempotencyKey)) {
            return AccountResponse.from(account);
        }
        Money dailyLimit = request.dailyWithdrawalLimit() == null
                ? null
                : Money.create(request.dailyWithdrawalLimit(), account.getBalance().getCurrency());
        account.updateDetails(request.alias(), dailyLimit, version, idempotencyKey);
        return AccountResponse.from(AccountCommands.saved(accountRepository, account));
    }
}
