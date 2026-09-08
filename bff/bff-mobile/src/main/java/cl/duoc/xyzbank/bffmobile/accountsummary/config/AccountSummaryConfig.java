package cl.duoc.xyzbank.bffmobile.accountsummary.config;

import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.ports.TransactionsPort;
import cl.duoc.xyzbank.bffmobile.accountsummary.application.usecases.AccountSummaryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountSummaryConfig {

    @Bean
    public AccountSummaryUseCase accountSummaryUseCase(AccountsPort accountsPort, TransactionsPort transactionsPort) {
        return new AccountSummaryUseCase(accountsPort, transactionsPort);
    }
}
