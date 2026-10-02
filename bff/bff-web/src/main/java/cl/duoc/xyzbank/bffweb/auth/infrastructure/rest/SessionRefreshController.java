package cl.duoc.xyzbank.bffweb.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters.IssuedTokens;
import cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters.RefreshTokenRejectedException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Rotates the customer's session by exchanging the refresh_token cookie at the authorization
 * server (bff-web-auth spec: the refresh token rotates on every use and detects reuse). A
 * refused grant — including reuse of a rotated-out token — clears the cookies, because the
 * authorization server has revoked the whole login. A refresh that could not complete is not a
 * refusal: AuthServerTokenClient raises DependencyUnavailableException, answered with the 503
 * ProblemDetail before any cookie is written, so the session is kept. Reachable without a valid
 * session cookie.
 */
@RestController
public class SessionRefreshController {

    private static final Duration REFRESH_TOKEN_COOKIE_TTL = Duration.ofDays(30);

    private final AuthServerTokenClient tokenClient;
    private final SessionCookieWriter cookieWriter;

    public SessionRefreshController(AuthServerTokenClient tokenClient, SessionCookieWriter cookieWriter) {
        this.tokenClient = tokenClient;
        this.cookieWriter = cookieWriter;
    }

    @PostMapping("/session/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            cookieWriter.clearSessionCookies(response);
            return ResponseEntity.status(401).build();
        }
        IssuedTokens tokens;
        try {
            tokens = tokenClient.refresh(refreshToken);
        } catch (RefreshTokenRejectedException exception) {
            cookieWriter.clearSessionCookies(response);
            return ResponseEntity.status(401).build();
        }
        cookieWriter.writeSessionCookies(
                response,
                tokens.accessToken(),
                tokens.accessTokenLifetime(),
                tokens.refreshToken(),
                REFRESH_TOKEN_COOKIE_TTL);
        return ResponseEntity.noContent().build();
    }
}
