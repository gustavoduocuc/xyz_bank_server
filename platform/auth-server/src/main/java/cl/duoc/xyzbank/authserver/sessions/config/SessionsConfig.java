package cl.duoc.xyzbank.authserver.sessions.config;

import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.sessions.application.usecases.DetectRefreshTokenReuseUseCase;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.StoredLogins;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.persistence.JdbcRotatedRefreshTokenRepository;
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
            OAuth2AuthorizationService authorizationService, RotatedRefreshTokenRepository rotatedTokens) {
        StoredLogins logins = new StoredLogins(authorizationService);
        return new DetectRefreshTokenReuseUseCase(logins, rotatedTokens, logins);
    }
}
