package cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.mdcKey);
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().set(CorrelationIdFilter.headerName, correlationId);
        }
        return execution.execute(request, body);
    }
}
