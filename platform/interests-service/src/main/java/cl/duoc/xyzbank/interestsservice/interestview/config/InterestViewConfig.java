package cl.duoc.xyzbank.interestsservice.interestview.config;

import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import cl.duoc.xyzbank.interestsservice.interestview.application.usecases.GetInterestSummaryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterestViewConfig {

    @Bean
    public GetInterestSummaryUseCase getInterestSummaryUseCase(CoreServicePort coreServicePort) {
        return new GetInterestSummaryUseCase(coreServicePort);
    }
}
