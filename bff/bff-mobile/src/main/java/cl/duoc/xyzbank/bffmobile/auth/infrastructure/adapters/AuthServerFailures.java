package cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.Set;

/**
 * Tells "the authorization server could not be reached or failed" apart from "it answered and
 * said no", by walking a failure's cause chain. Spring Security and Nimbus wrap the transport
 * failure several layers deep, and only the former must become a 503 instead of a 401/422
 * (bff-resilience spec, "An unavailable auth-server yields 503").
 */
public final class AuthServerFailures {

    private static final Set<String> UNAVAILABLE_ERROR_CODES = Set.of("server_error", "temporarily_unavailable");

    private AuthServerFailures() {
    }

    public static boolean isUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (isUnreachable(cause) || isServerError(cause) || isUnavailableError(cause)) {
                return true;
            }
        }
        return false;
    }

    /** Could not connect, timed out, or the breaker refused the call. */
    private static boolean isUnreachable(Throwable cause) {
        return cause instanceof IOException
                || cause instanceof ResourceAccessException
                || cause instanceof CallNotPermittedException;
    }

    private static boolean isServerError(Throwable cause) {
        return cause instanceof RestClientResponseException response && response.getStatusCode().is5xxServerError();
    }

    private static boolean isUnavailableError(Throwable cause) {
        OAuth2Error error = switch (cause) {
            case OAuth2AuthorizationException authorization -> authorization.getError();
            case OAuth2AuthenticationException authentication -> authentication.getError();
            default -> null;
        };
        return error != null && UNAVAILABLE_ERROR_CODES.contains(error.getErrorCode());
    }
}
