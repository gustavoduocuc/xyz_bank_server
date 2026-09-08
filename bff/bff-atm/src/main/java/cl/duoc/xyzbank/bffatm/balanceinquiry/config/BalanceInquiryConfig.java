package cl.duoc.xyzbank.bffatm.balanceinquiry.config;

import cl.duoc.xyzbank.bffatm.balanceinquiry.application.ports.AccountsPort;
import cl.duoc.xyzbank.bffatm.balanceinquiry.application.usecases.BalanceInquiryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BalanceInquiryConfig {

    @Bean
    public BalanceInquiryUseCase balanceInquiryUseCase(AccountsPort accountsPort) {
        return new BalanceInquiryUseCase(accountsPort);
    }
}
