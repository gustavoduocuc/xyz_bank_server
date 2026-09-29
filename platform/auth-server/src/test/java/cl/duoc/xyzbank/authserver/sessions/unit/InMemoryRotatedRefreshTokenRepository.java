package cl.duoc.xyzbank.authserver.sessions.unit;

import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRotatedRefreshTokenRepository implements RotatedRefreshTokenRepository {

    private final Map<String, String> authorizationsByToken = new ConcurrentHashMap<>();

    @Override
    public void record(String rotatedRefreshToken, String authorizationId, Instant rotatedAt) {
        authorizationsByToken.put(rotatedRefreshToken, authorizationId);
    }

    @Override
    public Optional<String> authorizationIdOf(String refreshToken) {
        return Optional.ofNullable(authorizationsByToken.get(refreshToken));
    }
}
