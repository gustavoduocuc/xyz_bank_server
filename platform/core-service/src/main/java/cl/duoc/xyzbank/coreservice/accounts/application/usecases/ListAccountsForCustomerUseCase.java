package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;

import java.util.List;

public class ListAccountsForCustomerUseCase {

    private final AccountRepository accountRepository;

    public ListAccountsForCustomerUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Customers live in customers-service; an identifier without accounts here yields an empty list. */
    public List<AccountSummaryResponse> execute(String customerId) {
        Id id = Id.create(customerId);
        return accountRepository.findByCustomerId(id).stream()
                .map(this::toResponse)
                .toList();
    }

    private AccountSummaryResponse toResponse(Account account) {
        return new AccountSummaryResponse(
                account.getId().getValue(),
                account.getAccountNumber().getValue(),
                account.getBalance().getAmount(),
                account.getBalance().getCurrency());
    }
}
