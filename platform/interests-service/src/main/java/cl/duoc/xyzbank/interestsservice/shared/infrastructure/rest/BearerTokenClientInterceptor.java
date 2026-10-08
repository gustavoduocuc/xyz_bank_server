package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Forwards the bearer token of the authenticated request (from the security context) as the
 * Authorization header on outgoing core-service calls. Does not overwrite an Authorization
 * header already set by the caller (e.g. service JWT for interest credits).
 */
@Component
public class BearerTokenClientInterceptor implements ClientHttpRequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsKey(AUTHORIZATION_HEADER)) {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken caller) {
                request.getHeaders().set(AUTHORIZATION_HEADER, BEARER_PREFIX + caller.getToken().getTokenValue());
            }
        }
        return execution.execute(request, body);
    }
}
