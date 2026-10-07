package cl.duoc.xyzbank.coreservice.accounts.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountsConfig {

    @Bean
    public ListAccountsForCustomerUseCase listAccountsForCustomerUseCase(AccountRepository accountRepository) {
        return new ListAccountsForCustomerUseCase(accountRepository);
    }

    @Bean
    public GetAccountBalanceUseCase getAccountBalanceUseCase(AccountRepository accountRepository) {
        return new GetAccountBalanceUseCase(accountRepository);
    }
}
