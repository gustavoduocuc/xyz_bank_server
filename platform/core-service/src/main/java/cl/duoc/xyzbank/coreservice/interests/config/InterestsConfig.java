package cl.duoc.xyzbank.coreservice.interests.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestCreditRepository;
import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.CreditInterestUseCase;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class InterestsConfig {

    @Bean
    public GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase(
            InterestSummaryRepository interestSummaryRepository) {
        return new GetAnnualInterestSummaryUseCase(interestSummaryRepository);
    }

    @Bean
    public CreditInterestUseCase creditInterestUseCase(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InterestSummaryRepository interestSummaryRepository,
            InterestCreditRepository interestCreditRepository,
            Clock clock) {
        return new CreditInterestUseCase(
                accountRepository,
                transactionRepository,
                interestSummaryRepository,
                interestCreditRepository,
                clock);
    }
}
