package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends the ATM session id embedded in the terminal session as {@code X-Atm-Session}.
 * core-service resolves the customer from that session, which it created at PIN verification.
 */
@Component
public class AtmSessionClientInterceptor implements ClientHttpRequestInterceptor {

    static final String ATM_SESSION_HEADER = "X-Atm-Session";

    private final JwtCallerContextAdapter tokenAdapter;

    public AtmSessionClientInterceptor(JwtCallerContextAdapter tokenAdapter) {
        this.tokenAdapter = tokenAdapter;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String sessionToken = MDC.get(CallerContextInterceptor.USER_TOKEN_MDC_KEY);
        if (sessionToken != null && !sessionToken.isBlank()) {
            tokenAdapter.atmSessionIdOf(sessionToken)
                    .ifPresent(atmSessionId -> request.getHeaders().set(ATM_SESSION_HEADER, atmSessionId));
        }
        return execution.execute(request, body);
    }
}
