package cl.duoc.xyzbank.authserver.sessions.infrastructure.persistence;

import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Rotated-out refresh tokens in auth-server's database (table from Flyway V2), stored as
 * SHA-256 hashes: recognising a replayed token only needs its hash, and a leaked table then
 * gives nothing an attacker could present.
 */
public class JdbcRotatedRefreshTokenRepository implements RotatedRefreshTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRotatedRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String rotatedRefreshToken, String authorizationId, Instant rotatedAt) {
        jdbcTemplate.update("""
                INSERT INTO rotated_refresh_tokens (token_hash, authorization_id, rotated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (token_hash) DO NOTHING
                """,
                hashOf(rotatedRefreshToken), authorizationId, Timestamp.from(rotatedAt));
    }

    @Override
    public Optional<String> authorizationIdOf(String refreshToken) {
        return jdbcTemplate.queryForList(
                "SELECT authorization_id FROM rotated_refresh_tokens WHERE token_hash = ?",
                String.class,
                hashOf(refreshToken)).stream().findFirst();
    }

    private static String hashOf(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
