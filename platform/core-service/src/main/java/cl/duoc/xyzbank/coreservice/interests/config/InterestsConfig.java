package cl.duoc.xyzbank.coreservice.interests.config;

import cl.duoc.xyzbank.coredomain.interests.domain.repositories.InterestSummaryRepository;
import cl.duoc.xyzbank.coreservice.interests.application.usecases.GetAnnualInterestSummaryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterestsConfig {

    @Bean
    public GetAnnualInterestSummaryUseCase getAnnualInterestSummaryUseCase(
            InterestSummaryRepository interestSummaryRepository) {
        return new GetAnnualInterestSummaryUseCase(interestSummaryRepository);
    }
}
