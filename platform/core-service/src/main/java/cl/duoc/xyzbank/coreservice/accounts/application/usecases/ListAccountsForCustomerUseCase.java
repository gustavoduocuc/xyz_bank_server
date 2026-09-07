package cl.duoc.xyzbank.coreservice.accounts.application.usecases;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coreservice.accounts.application.dto.AccountSummaryResponse;

import java.util.List;

public class ListAccountsForCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public ListAccountsForCustomerUseCase(
            CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    public List<AccountSummaryResponse> execute(String customerId) {
        Id id = Id.create(customerId);
        customerRepository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Customer " + customerId + " not found"));

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
