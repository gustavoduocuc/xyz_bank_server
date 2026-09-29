package cl.duoc.xyzbank.bffweb.auth.infrastructure.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * On a successful OIDC login, keeps the tokens the authorization server just issued as the
 * session: the access token becomes the session cookie and its refresh token the refresh_token
 * cookie. bff-web mints nothing and calls no other service — the authorization-code exchange
 * Spring already performed is the only call (bff-web-auth spec).
 *
 * <p>This handler runs inside the Spring Security filter chain rather than under
 * DispatcherServlet, so a missing token pair must be turned into a response here.
 */
@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration REFRESH_TOKEN_COOKIE_TTL = Duration.ofDays(30);
    private static final Duration FALLBACK_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final SessionCookieWriter cookieWriter;

    public OidcLoginSuccessHandler(
            OAuth2AuthorizedClientRepository authorizedClients, SessionCookieWriter cookieWriter) {
        this.authorizedClients = authorizedClients;
        this.cookieWriter = cookieWriter;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        OAuth2AuthorizedClient client = authorizedClient(authentication, request);
        if (client == null || client.getRefreshToken() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Duration accessTokenTtl = accessTokenTtl(client.getAccessToken());
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        cookieWriter.writeSessionCookies(
                response,
                client.getAccessToken().getTokenValue(),
                accessTokenTtl,
                client.getRefreshToken().getTokenValue(),
                REFRESH_TOKEN_COOKIE_TTL);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private OAuth2AuthorizedClient authorizedClient(Authentication authentication, HttpServletRequest request) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return null;
        }
        return authorizedClients.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), authentication, request);
    }

    private static Duration accessTokenTtl(OAuth2AccessToken accessToken) {
        Instant expiresAt = accessToken.getExpiresAt();
        if (expiresAt == null) {
            return FALLBACK_ACCESS_TOKEN_TTL;
        }
        return Duration.between(Instant.now(), expiresAt);
    }
}
