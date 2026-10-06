package cl.duoc.xyzbank.authserver.sessions.unit;

import cl.duoc.xyzbank.authserver.sessions.application.dto.RefreshTokenVerdict;
import cl.duoc.xyzbank.authserver.sessions.application.usecases.DetectRefreshTokenReuseUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what core-domain's RefreshTokenRecordTest checked (case 1: a fresh token is usable;
 * case 3: reusing a rotated token is detected), now against auth-server refresh tokens
 * (adopt-oauth2-tokens-between-services design.md Decision 6).
 */
@DisplayName("The DetectRefreshTokenReuseUseCase")
class DetectRefreshTokenReuseUseCaseTest {

    /*
     * Cases:
     * 1. A login's current refresh token is accepted and revokes nothing
     * 2. A refresh token that was rotated out is recognised as reuse and revokes its login
     * 3. A refresh token never seen is rejected without revoking anything
     */

    private final List<String> revokedLogins = new ArrayList<>();
    private InMemoryRotatedRefreshTokenRepository rotatedTokens;
    private DetectRefreshTokenReuseUseCase useCase;

    @BeforeEach
    void setUp() {
        rotatedTokens = new InMemoryRotatedRefreshTokenRepository();
        Set<String> currentTokens = Set.of("R2");
        useCase = new DetectRefreshTokenReuseUseCase(currentTokens::contains, rotatedTokens, revokedLogins::add);
    }

    @Test
    @DisplayName("accepts a login's current refresh token and revokes nothing")
    void acceptsALoginsCurrentRefreshToken() {
        RefreshTokenVerdict verdict = useCase.execute("R2");

        assertEquals(RefreshTokenVerdict.ACCEPTED, verdict);
        assertTrue(revokedLogins.isEmpty());
    }

    @Test
    @DisplayName("recognises a rotated-out refresh token as reuse and revokes its login")
    void recognisesARotatedOutRefreshTokenAsReuseAndRevokesItsLogin() {
        rotatedTokens.record("R1", "login-1", Instant.now());

        RefreshTokenVerdict verdict = useCase.execute("R1");

        assertEquals(RefreshTokenVerdict.REUSED, verdict);
        assertEquals(List.of("login-1"), revokedLogins);
    }

    @Test
    @DisplayName("rejects a refresh token never seen without revoking anything")
    void rejectsARefreshTokenNeverSeenWithoutRevokingAnything() {
        RefreshTokenVerdict verdict = useCase.execute("forged");

        assertEquals(RefreshTokenVerdict.UNKNOWN, verdict);
        assertTrue(revokedLogins.isEmpty());
    }
}
