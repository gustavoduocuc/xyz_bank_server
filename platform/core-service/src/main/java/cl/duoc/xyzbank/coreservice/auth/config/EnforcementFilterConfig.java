package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.EnforcementFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class EnforcementFilterConfig {

    @Value("${security.enforcement.enabled}")
    private boolean enforcementEnabled;

    @Bean
    public FilterRegistrationBean<EnforcementFilter> enforcementFilter(
            AccessTokenVerifier accessTokenVerifier,
            AtmSessionLookup atmSessionLookup,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        FilterRegistrationBean<EnforcementFilter> registration = new FilterRegistrationBean<>(new EnforcementFilter(
                enforcementEnabled, accessTokenVerifier, atmSessionLookup, accountRepository, transactionRepository));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(2);
        return registration;
    }
}
