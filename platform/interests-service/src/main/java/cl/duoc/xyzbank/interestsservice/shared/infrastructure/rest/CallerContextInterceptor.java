package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.AccessTokenValidator;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.InsufficientScopeException;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.InvalidAccessTokenException;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.ValidatedAccessToken;
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

    private final AccessTokenValidator validator;

    public CallerContextInterceptor(AccessTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String bearerToken = extractBearerToken(request);
        if (bearerToken == null) {
            throw new InvalidAccessTokenException("A valid access token is required");
        }
        ValidatedAccessToken token = validator.validate(bearerToken);
        EndpointRequirements.requirementFor(request.getMethod(), request.getRequestURI())
                .ifPresent(requirement -> {
                    if (!requirement.channels().contains(token.channel()) || !token.scopes().contains(requirement.scope())) {
                        throw new InsufficientScopeException("The token's scope does not permit this operation");
                    }
                });
        // Relayed unchanged to core-service by BearerTokenClientInterceptor (token relay)
        MDC.put(USER_TOKEN_MDC_KEY, bearerToken);
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
