package cl.duoc.xyzbank.authserver.sessions.application.ports;

import java.time.Instant;
import java.util.Optional;

/**
 * Remembers refresh tokens that were rotated out, and the login (authorization) each belonged
 * to, so presenting one again can be recognised as reuse.
 */
public interface RotatedRefreshTokenRepository {

    void record(String rotatedRefreshToken, String authorizationId, Instant rotatedAt);

    Optional<String> authorizationIdOf(String refreshToken);
}
