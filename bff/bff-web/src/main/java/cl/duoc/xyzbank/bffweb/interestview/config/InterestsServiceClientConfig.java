package cl.duoc.xyzbank.bffweb.interestview.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class InterestsServiceClientConfig {

    @Bean
    @Qualifier("interestsServiceClient")
    public RestClient interestsServiceClient(
            @Value("${interests-service.base-url}") String baseUrl,
            @Value("${interests-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${interests-service.read-timeout-ms}") int readTimeoutMs,
            CorrelationIdClientInterceptor correlationIdClientInterceptor,
            BearerTokenClientInterceptor bearerTokenClientInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdClientInterceptor)
                .requestInterceptor(bearerTokenClientInterceptor)
                .build();
    }
}
