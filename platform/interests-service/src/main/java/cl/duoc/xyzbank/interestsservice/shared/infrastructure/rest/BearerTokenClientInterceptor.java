package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Forwards the user token stashed in MDC by {@link CallerContextInterceptor} as the
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
            String userToken = MDC.get(CallerContextInterceptor.USER_TOKEN_MDC_KEY);
            if (userToken != null && !userToken.isBlank()) {
                request.getHeaders().set(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken);
            }
        }
        return execution.execute(request, body);
    }
}
