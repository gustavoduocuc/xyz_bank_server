package cl.duoc.xyzbank.bffweb.shared.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreServiceClientConfig {

    @Bean
    public RestClient coreServiceClient(
            ObservationRegistry observationRegistry,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs,
            CorrelationIdClientInterceptor correlationIdClientInterceptor,
            BearerTokenClientInterceptor bearerTokenClientInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .observationRegistry(observationRegistry)
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdClientInterceptor)
                .requestInterceptor(bearerTokenClientInterceptor)
                .build();
    }
}
