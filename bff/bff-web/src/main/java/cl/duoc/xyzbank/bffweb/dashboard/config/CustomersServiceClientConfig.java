package cl.duoc.xyzbank.bffweb.dashboard.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CustomersServiceClientConfig {

    @Bean
    @Qualifier("customersServiceClient")
    public RestClient customersServiceClient(
            ObservationRegistry observationRegistry,
            @Value("${customers-service.base-url}") String baseUrl,
            @Value("${customers-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${customers-service.read-timeout-ms}") int readTimeoutMs,
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
