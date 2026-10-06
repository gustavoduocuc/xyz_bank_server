package cl.duoc.xyzbank.authserver.shared.config;

import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.RotationRecordingAuthorizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Keeps every authorization (codes and the tokens issued from them) and every consent in
 * auth-server's own database instead of Spring Authorization Server's in-memory defaults
 * (persist-auth-server-state design.md Decision 4), so a restart loses no OAuth state and a
 * code's single use is enforced from the stored "invalidated" flag.
 */
@Configuration
public class AuthorizationServerPersistenceConfig {

    /**
     * The JDBC store, decorated so that every refresh-token rotation also records the replaced
     * token (reuse detection, adopt-oauth2-tokens-between-services design.md Decision 3).
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository,
            RotatedRefreshTokenRepository rotatedRefreshTokenRepository,
            PlatformTransactionManager transactionManager) {
        return new RotationRecordingAuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository),
                rotatedRefreshTokenRepository,
                transactionManager);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }
}
