package cl.duoc.xyzbank.bffweb.interestview.config;

import cl.duoc.xyzbank.bffweb.interestview.application.ports.InterestPort;
import cl.duoc.xyzbank.bffweb.interestview.application.usecases.InterestViewUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterestViewConfig {

    @Bean
    public InterestViewUseCase interestViewUseCase(InterestPort interestPort) {
        return new InterestViewUseCase(interestPort);
    }
}
