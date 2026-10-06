package cl.duoc.xyzbank.authserver.sessions.integration;

import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.RotationRecordingAuthorizationService;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.persistence.JdbcRotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.IsolatedSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what core-domain's RefreshTokenRecordTest case 2 checked (rotation marks the old
 * token), now for auth-server's stored logins.
 */
@DisplayName("The rotation-recording authorization service")
class RotationRecordingAuthorizationServiceIT extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Saving a login whose refresh token changed records the old token for that login
     * 2. Saving a login for the first time records nothing
     * 3. Saving a login again with the same refresh token records nothing
     */

    private JdbcRotatedRefreshTokenRepository rotatedTokens;
    private RotationRecordingAuthorizationService service;
    private RegisteredClient client;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = IsolatedSchema.freshlyMigrated(POSTGRES, "rotation_it");
        JdbcRegisteredClientRepository clients = new JdbcRegisteredClientRepository(jdbcTemplate);
        client = RegisteredClient.withId("bff-web")
                .clientId("bff-web")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .build();
        clients.save(client);
        rotatedTokens = new JdbcRotatedRefreshTokenRepository(jdbcTemplate);
        service = new RotationRecordingAuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbcTemplate, clients),
                rotatedTokens,
                new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Test
    @DisplayName("records the old refresh token when a login is saved with a new one")
    void recordsTheOldRefreshTokenWhenALoginIsSavedWithANewOne() {
        service.save(loginWithRefreshToken("R1"));

        service.save(OAuth2Authorization.from(loginWithRefreshToken("R1")).refreshToken(refreshToken("R2")).build());

        assertEquals("login-1", rotatedTokens.authorizationIdOf("R1").orElseThrow());
        assertTrue(rotatedTokens.authorizationIdOf("R2").isEmpty());
    }

    @Test
    @DisplayName("records nothing when a login is saved for the first time")
    void recordsNothingWhenALoginIsSavedForTheFirstTime() {
        service.save(loginWithRefreshToken("R1"));

        assertTrue(rotatedTokens.authorizationIdOf("R1").isEmpty());
    }

    @Test
    @DisplayName("records nothing when a login is saved again with the same refresh token")
    void recordsNothingWhenALoginIsSavedAgainWithTheSameRefreshToken() {
        service.save(loginWithRefreshToken("R1"));

        service.save(loginWithRefreshToken("R1"));

        assertTrue(rotatedTokens.authorizationIdOf("R1").isEmpty());
    }

    private OAuth2Authorization loginWithRefreshToken(String value) {
        return OAuth2Authorization.withRegisteredClient(client)
                .id("login-1")
                .principalName("demo")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .refreshToken(refreshToken(value))
                .build();
    }

    private static OAuth2RefreshToken refreshToken(String value) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new OAuth2RefreshToken(value, issuedAt, issuedAt.plus(30, ChronoUnit.DAYS));
    }
}
