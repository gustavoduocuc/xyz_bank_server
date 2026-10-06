package cl.duoc.xyzbank.bffweb.shared.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.shared.application.CallerContextResolver;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the caller's identity from the "session" cookie, which holds the access token the
 * authorization server issued to bff-web (caller-context spec: "A validated channel credential
 * is the sole source of caller identity"). Rejects a missing, expired, or otherwise invalid
 * cookie before any handler runs. Stores the raw token in MDC (mirroring CorrelationIdFilter's
 * pattern) so BearerTokenClientInterceptor can forward it as the Authorization header on
 * outgoing calls, and clears it once the request completes.
 */
@Component
public class CallerContextInterceptor implements HandlerInterceptor {

    static final String SESSION_COOKIE_NAME = "session";
    public static final String USER_TOKEN_MDC_KEY = "userToken";

    private final CallerContextResolver callerContextResolver;

    public CallerContextInterceptor(CallerContextResolver callerContextResolver) {
        this.callerContextResolver = callerContextResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String sessionJwt = sessionCookieValue(request);
        if (sessionJwt == null) {
            throw CallerIdentityException.invalid("Missing session cookie");
        }
        CallerContext callerContext = callerContextResolver.resolve(sessionJwt);
        if (callerContext.channel() != Channel.WEB) {
            throw CallerIdentityException.forbidden("This endpoint requires the web channel");
        }
        MDC.put(USER_TOKEN_MDC_KEY, sessionJwt);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        MDC.remove(USER_TOKEN_MDC_KEY);
    }

    private String sessionCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
