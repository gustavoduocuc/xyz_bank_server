package cl.duoc.xyzbank.coreservice.accounts.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.CustomerRepository;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetAccountBalanceUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.GetCustomerProfileUseCase;
import cl.duoc.xyzbank.coreservice.accounts.application.usecases.ListAccountsForCustomerUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountsConfig {

    @Bean
    public GetCustomerProfileUseCase getCustomerProfileUseCase(CustomerRepository customerRepository) {
        return new GetCustomerProfileUseCase(customerRepository);
    }

    @Bean
    public ListAccountsForCustomerUseCase listAccountsForCustomerUseCase(
            CustomerRepository customerRepository, AccountRepository accountRepository) {
        return new ListAccountsForCustomerUseCase(customerRepository, accountRepository);
    }

    @Bean
    public GetAccountBalanceUseCase getAccountBalanceUseCase(AccountRepository accountRepository) {
        return new GetAccountBalanceUseCase(accountRepository);
    }
}
