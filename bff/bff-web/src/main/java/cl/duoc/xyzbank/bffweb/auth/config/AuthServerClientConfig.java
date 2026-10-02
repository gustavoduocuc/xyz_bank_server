package cl.duoc.xyzbank.bffweb.auth.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CircuitBreakerClientInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class AuthServerClientConfig {

    @Bean
    public AuthServerHttpClients authServerHttpClients(
            @Value("${auth-server.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${auth-server.read-timeout-ms}") int readTimeoutMs,
            CircuitBreakerRegistry circuitBreakers) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new AuthServerHttpClients(
                requestFactory, new CircuitBreakerClientInterceptor(circuitBreakers.circuitBreaker("authServer")));
    }
}
