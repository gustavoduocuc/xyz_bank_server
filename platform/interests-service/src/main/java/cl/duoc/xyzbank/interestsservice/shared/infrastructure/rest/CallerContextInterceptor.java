package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Stashes the inbound Authorization bearer token in MDC so
 * {@link BearerTokenClientInterceptor} can forward it on outgoing RestClient calls.
 */
@Component
public class CallerContextInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_TOKEN_MDC_KEY = "userToken";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String bearerToken = extractBearerToken(request);
        if (bearerToken != null) {
            MDC.put(USER_TOKEN_MDC_KEY, bearerToken);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        MDC.remove(USER_TOKEN_MDC_KEY);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }
}
