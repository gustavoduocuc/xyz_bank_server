package cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.shared.application.CallerContextResolver;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the caller's identity from the device-bound JWT presented as a bearer token
 * (caller-context spec: "A validated channel credential is the sole source of caller
 * identity"), rejecting a missing, expired, or otherwise invalid token before any handler
 * runs. Also verifies the caller's declared X-Device-Id header matches the token's
 * {@code device_id} claim -- a token stolen from one device must not authenticate requests
 * claiming to come from another.
 */
@Component
public class CallerContextInterceptor implements HandlerInterceptor {

    static final String DEVICE_ID_HEADER = "X-Device-Id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
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
        String bearerToken = extractBearerToken(request);
        if (bearerToken == null) {
            throw CallerIdentityException.invalid("Missing bearer token");
        }
        CallerContext callerContext = callerContextResolver.resolve(bearerToken);
        if (callerContext.channel() != Channel.MOBILE) {
            throw CallerIdentityException.forbidden("This endpoint requires the mobile channel");
        }
        String declaredDeviceId = request.getHeader(DEVICE_ID_HEADER);
        String boundDeviceId = callerContext.terminalId().orElse(null);
        if (declaredDeviceId == null || boundDeviceId == null || !declaredDeviceId.equals(boundDeviceId)) {
            throw CallerIdentityException.invalid("Device identifier does not match the token's bound device");
        }
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
