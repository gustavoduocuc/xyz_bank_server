package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Forwards the resolved terminal session (stashed in MDC by CallerContextInterceptor) as the
 * Authorization header. ClientCredentialsTokenInterceptor runs after this one and replaces
 * that header with bff-atm's own client token, which is the credential core-service sees.
 */
@Component
public class BearerTokenClientInterceptor implements ClientHttpRequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String userToken = MDC.get(CallerContextInterceptor.USER_TOKEN_MDC_KEY);
        if (userToken != null && !userToken.isBlank()) {
            request.getHeaders().set(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken);
        }
        return execution.execute(request, body);
    }
}
