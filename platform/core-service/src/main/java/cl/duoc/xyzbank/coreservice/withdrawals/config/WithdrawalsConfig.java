package cl.duoc.xyzbank.coreservice.withdrawals.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coredomain.withdrawals.domain.repositories.WithdrawalRepository;
import cl.duoc.xyzbank.coreservice.withdrawals.application.usecases.WithdrawAccountUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;

@Configuration
public class WithdrawalsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public WithdrawAccountUseCase withdrawAccountUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            WithdrawalRepository withdrawalRepository,
            @Value("${app.withdrawals.daily-limit}") BigDecimal dailyLimit,
            Clock clock) {
        return new WithdrawAccountUseCase(accountRepository, transactionRepository, withdrawalRepository, dailyLimit, clock);
    }
}
