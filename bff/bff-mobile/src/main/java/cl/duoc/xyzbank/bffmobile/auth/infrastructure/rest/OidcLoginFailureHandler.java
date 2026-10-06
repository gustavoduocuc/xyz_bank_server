package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AuthServerFailures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * On a failed/denied OIDC callback, no session is issued and no refresh token is requested. A
 * login that failed because the authorization server could not be reached, timed out, failed or
 * has its circuit open answers the 503 ProblemDetail rather than 401: the customer's credentials
 * were never judged (bff-resilience spec). Written here because this handler runs in the
 * security filter chain, outside BffExceptionHandler's reach.
 */
@Component
public class OidcLoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    public OidcLoginFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (!AuthServerFailures.isUnavailable(exception)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Authorization server is unavailable");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
