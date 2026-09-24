package cl.duoc.xyzbank.interestsservice.interests.config;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.InterestCalculatedPublisher;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.ApplyAnnualInterestUseCase;
import cl.duoc.xyzbank.interestsservice.interests.application.usecases.RecordInterestCreditResultUseCase;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InMemoryInterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.repositories.InterestCalculationRepository;
import cl.duoc.xyzbank.interestsservice.interests.domain.services.InterestRatePolicy;
import cl.duoc.xyzbank.interestsservice.interestview.application.ports.CoreServicePort;
import org.springframework.beans.factory.ObjectProvider;
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
    public InterestCalculationRepository interestCalculationRepository() {
        return new InMemoryInterestCalculationRepository();
    }

    @Bean
    public RecordInterestCreditResultUseCase recordInterestCreditResultUseCase(
            InterestCalculationRepository calculations) {
        return new RecordInterestCreditResultUseCase(calculations);
    }

    @Bean
    public ApplyAnnualInterestUseCase applyAnnualInterestUseCase(
            CoreServicePort coreServicePort,
            InterestRatePolicy interestRatePolicy,
            InterestCalculationRepository calculations,
            ObjectProvider<InterestCalculatedPublisher> publishers,
            @Value("${interests.kafka.enabled:false}") boolean creditViaKafka) {
        if (!creditViaKafka) {
            return new ApplyAnnualInterestUseCase(coreServicePort, interestRatePolicy);
        }
        return new ApplyAnnualInterestUseCase(
                coreServicePort,
                interestRatePolicy,
                calculations,
                publishers.getObject());
    }
}
