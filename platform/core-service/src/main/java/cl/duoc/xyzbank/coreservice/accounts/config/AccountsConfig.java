package cl.duoc.xyzbank.coreservice.accounts.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectory;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.CloseAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.OpenAccountUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.UpdateAccountUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountsConfig {

    @Bean
    public ListAccountsForCustomerUseCase listAccountsForCustomerUseCase(AccountRepository accountRepository) {
        return new ListAccountsForCustomerUseCase(accountRepository);
    }

    @Bean
    public OpenAccountUseCase openAccountUseCase(
            AccountRepository accountRepository, CustomerDirectory customerDirectory) {
        return new OpenAccountUseCase(accountRepository, customerDirectory);
    }

    @Bean
    public UpdateAccountUseCase updateAccountUseCase(AccountRepository accountRepository) {
        return new UpdateAccountUseCase(accountRepository);
    }

    @Bean
    public CloseAccountUseCase closeAccountUseCase(AccountRepository accountRepository) {
        return new CloseAccountUseCase(accountRepository);
    }

    @Bean
    public GetAccountBalanceUseCase getAccountBalanceUseCase(AccountRepository accountRepository) {
        return new GetAccountBalanceUseCase(accountRepository);
    }
}
