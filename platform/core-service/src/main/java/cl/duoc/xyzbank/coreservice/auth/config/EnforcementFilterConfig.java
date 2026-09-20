package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.EnforcementFilter;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class EnforcementFilterConfig {

    @Value("${security.enforcement.enabled}")
    private boolean enforcementEnabled;

    @Value("${security.service-credentials.web}")
    private String webServiceCredential;

    @Value("${security.service-credentials.mobile}")
    private String mobileServiceCredential;

    @Value("${security.service-credentials.atm}")
    private String atmServiceCredential;

    @Value("${security.service-credentials.interests}")
    private String interestsServiceCredential;

    @Bean
    public FilterRegistrationBean<EnforcementFilter> enforcementFilter(
            JwtCallerContextAdapter tokenAdapter,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        Map<String, String> serviceCredentials = Map.of(
                "web", webServiceCredential,
                "mobile", mobileServiceCredential,
                "atm", atmServiceCredential,
                "interests", interestsServiceCredential);
        FilterRegistrationBean<EnforcementFilter> registration = new FilterRegistrationBean<>(new EnforcementFilter(
                enforcementEnabled, serviceCredentials, tokenAdapter, accountRepository, transactionRepository));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(2);
        return registration;
    }
}
