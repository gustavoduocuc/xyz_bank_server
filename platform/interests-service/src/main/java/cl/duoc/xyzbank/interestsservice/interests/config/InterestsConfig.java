package cl.duoc.xyzbank.interestsservice.interests.config;

import cl.duoc.xyzbank.interestsservice.interests.application.usecases.ApplyAnnualInterestUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class InterestsConfig {

    @Bean
    public InterestRatePolicy interestRatePolicy(
            @Value("${interests.annual-rate}") BigDecimal annualRate) {
        return new InterestRatePolicy(annualRate);
    }

    @Bean
    public ApplyAnnualInterestUseCase applyAnnualInterestUseCase(
            CoreServicePort coreServicePort,
            InterestRatePolicy interestRatePolicy) {
        return new ApplyAnnualInterestUseCase(coreServicePort, interestRatePolicy);
    }
}
