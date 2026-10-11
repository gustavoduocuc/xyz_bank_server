package cl.duoc.xyzbank.authserver.sessions.config;

import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.sessions.application.usecases.DetectRefreshTokenReuseUseCase;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.StoredLogins;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.persistence.JdbcRotatedRefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import cl.duoc.xyzbank.authserver.sessions.application.ports.SecurityAlertPublisher;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.KafkaSecurityAlertPublisher;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.CustomerLoginLookupAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

@Configuration
public class SessionsConfig {

    @Bean
    public RotatedRefreshTokenRepository rotatedRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRotatedRefreshTokenRepository(jdbcTemplate);
    }

    @Bean
    public DetectRefreshTokenReuseUseCase detectRefreshTokenReuseUseCase(
            OAuth2AuthorizationService authorizationService,
            RotatedRefreshTokenRepository rotatedTokens,
            CustomerLoginRepository customerLogins,
            SecurityAlertPublisher alerts) {
        StoredLogins logins = new StoredLogins(authorizationService);
        return new DetectRefreshTokenReuseUseCase(
                logins, rotatedTokens, logins, new CustomerLoginLookupAdapter(customerLogins), alerts);
    }

    @Bean
    @ConditionalOnProperty(name = "app.events.security-alerts.enabled", havingValue = "true")
    public SecurityAlertPublisher kafkaSecurityAlertPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.events.security-alerts.topic:security.alerts}") String topic) {
        return new KafkaSecurityAlertPublisher(kafkaTemplate, objectMapper, topic);
    }

    @Bean
    @ConditionalOnProperty(name = "app.events.security-alerts.enabled", havingValue = "false", matchIfMissing = true)
    public SecurityAlertPublisher noOpSecurityAlertPublisher() {
        return customerId -> {
        };
    }
}
