package cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.sessions.application.ports.RotatedRefreshTokenRepository;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorates the authorization store: when a login is saved with a refresh token different from
 * the one stored (a rotation), the replaced token is remembered for that login, in the same
 * transaction as the save. Spring Authorization Server overwrites the old token in place, so
 * without this a replayed token would just look unknown instead of revealing reuse
 * (adopt-oauth2-tokens-between-services design.md Decision 3).
 */
public class RotationRecordingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RotatedRefreshTokenRepository rotatedTokens;
    private final TransactionTemplate transaction;

    public RotationRecordingAuthorizationService(
            OAuth2AuthorizationService delegate,
            RotatedRefreshTokenRepository rotatedTokens,
            PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.rotatedTokens = rotatedTokens;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        transaction.executeWithoutResult(status -> {
            replacedRefreshToken(authorization).ifPresent(replaced ->
                    rotatedTokens.record(replaced, authorization.getId(), Instant.now()));
            delegate.save(authorization);
        });
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private Optional<String> replacedRefreshToken(OAuth2Authorization authorization) {
        Optional<String> stored = refreshTokenOf(delegate.findById(authorization.getId()));
        Optional<String> incoming = refreshTokenOf(authorization);
        return stored.filter(previous -> incoming.isPresent() && !Objects.equals(previous, incoming.get()));
    }

    private static Optional<String> refreshTokenOf(OAuth2Authorization authorization) {
        return Optional.ofNullable(authorization)
                .map(OAuth2Authorization::getRefreshToken)
                .map(OAuth2Authorization.Token::getToken)
                .map(OAuth2RefreshToken::getTokenValue);
    }
}
