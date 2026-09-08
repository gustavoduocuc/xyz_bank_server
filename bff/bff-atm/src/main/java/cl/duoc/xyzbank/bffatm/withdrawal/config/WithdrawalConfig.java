package cl.duoc.xyzbank.bffatm.withdrawal.config;

import cl.duoc.xyzbank.bffatm.withdrawal.application.ports.WithdrawalsPort;
import cl.duoc.xyzbank.bffatm.withdrawal.application.usecases.WithdrawalUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WithdrawalConfig {

    @Bean
    public WithdrawalUseCase withdrawalUseCase(WithdrawalsPort withdrawalsPort) {
        return new WithdrawalUseCase(withdrawalsPort);
    }
}
