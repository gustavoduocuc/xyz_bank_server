package cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.sessions.application.ports.CurrentRefreshTokens;
import cl.duoc.xyzbank.authserver.sessions.application.ports.LoginRevoker;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Optional;

/**
 * The logins (authorizations) Spring Authorization Server keeps: a refresh token is current
 * when some stored login holds it, and revoking a login removes it with all its tokens.
 */
public class StoredLogins implements CurrentRefreshTokens, LoginRevoker {

    private final OAuth2AuthorizationService authorizations;

    public StoredLogins(OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @Override
    public boolean isCurrent(String refreshToken) {
        return authorizations.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN) != null;
    }

    @Override
    public Optional<String> revoke(String authorizationId) {
        return Optional.ofNullable(authorizations.findById(authorizationId)).map(this::remove);
    }

    private String remove(OAuth2Authorization authorization) {
        authorizations.remove(authorization);
        return authorization.getPrincipalName();
    }
}
