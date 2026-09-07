package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountBalanceResponse;

public class GetAccountBalanceUseCase {

    private final AccountRepository accountRepository;

    public GetAccountBalanceUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountBalanceResponse execute(String accountId) {
        Id id = Id.create(accountId);
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Account " + accountId + " not found"));

        return toResponse(account);
    }

    private AccountBalanceResponse toResponse(Account account) {
        return new AccountBalanceResponse(
                account.getId().getValue(),
                account.getBalance().getAmount(),
                account.getBalance().getCurrency());
    }
}
