package cl.duoc.xyzbank.authserver.sessions.integration;

import cl.duoc.xyzbank.authserver.sessions.infrastructure.persistence.JdbcRotatedRefreshTokenRepository;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.IsolatedSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases 1-2 migrated from core-service's JpaRefreshTokenRepositoryIT (save/find by token hash,
 * empty when absent); case 3 of that test (chain revocation) is covered end to end by
 * RefreshTokenRotationE2ETest.
 */
@DisplayName("The JDBC rotated refresh token repository")
class JdbcRotatedRefreshTokenRepositoryIT extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Records a rotated-out refresh token and finds its login by the token
     * 2. Finds nothing for a token that was never recorded
     * 3. Stores only a hash of the token, never the token itself
     */

    private JdbcTemplate jdbcTemplate;
    private JdbcRotatedRefreshTokenRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = IsolatedSchema.freshlyMigrated(POSTGRES, "rotated_tokens_it");
        repository = new JdbcRotatedRefreshTokenRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("records a rotated-out refresh token and finds its login by the token")
    void recordsARotatedOutRefreshTokenAndFindsItsLoginByTheToken() {
        repository.record("refresh-token-1", "login-1", Instant.now());

        assertEquals("login-1", repository.authorizationIdOf("refresh-token-1").orElseThrow());
    }

    @Test
    @DisplayName("finds nothing for a token that was never recorded")
    void findsNothingForATokenThatWasNeverRecorded() {
        assertTrue(repository.authorizationIdOf("never-issued").isEmpty());
    }

    @Test
    @DisplayName("stores only a hash of the token, never the token itself")
    void storesOnlyAHashOfTheToken() {
        repository.record("refresh-token-2", "login-2", Instant.now());

        List<String> stored = jdbcTemplate.queryForList("SELECT token_hash FROM rotated_refresh_tokens", String.class);
        assertEquals(1, stored.size());
        assertFalse(stored.get(0).contains("refresh-token-2"));
        assertEquals(64, stored.get(0).length());
    }
}
