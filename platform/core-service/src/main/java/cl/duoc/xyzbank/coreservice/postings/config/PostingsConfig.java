package cl.duoc.xyzbank.coreservice.postings.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.postings.application.ports.PostingRepository;
import cl.duoc.xyzbank.coreservice.postings.application.usecases.ApplyPostingsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PostingsConfig {

    @Bean
    public ApplyPostingsUseCase applyPostingsUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PostingRepository postingRepository,
            Clock clock) {
        return new ApplyPostingsUseCase(accountRepository, transactionRepository, postingRepository, clock);
    }
}
