package cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.sessions.application.dto.RefreshTokenVerdict;
import cl.duoc.xyzbank.authserver.sessions.application.usecases.DetectRefreshTokenReuseUseCase;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

/**
 * Stands in front of Spring Authorization Server's refresh_token grant: a refresh token that
 * is not a login's current one is refused with invalid_grant, and if it is a rotated-out one
 * the login is revoked first (adopt-oauth2-tokens-between-services design.md Decision 3).
 */
public class RefreshTokenGrantGuard implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final DetectRefreshTokenReuseUseCase detectReuse;

    public RefreshTokenGrantGuard(AuthenticationProvider delegate, DetectRefreshTokenReuseUseCase detectReuse) {
        this.delegate = delegate;
        this.detectReuse = detectReuse;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String refreshToken = ((OAuth2RefreshTokenAuthenticationToken) authentication).getRefreshToken();
        if (detectReuse.execute(refreshToken) != RefreshTokenVerdict.ACCEPTED) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
