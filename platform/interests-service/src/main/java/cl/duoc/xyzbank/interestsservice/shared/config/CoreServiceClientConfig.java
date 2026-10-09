package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest.BearerTokenClientInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreServiceClientConfig {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true", matchIfMissing = true)
    public RestClient.Builder loadBalancedRestClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false")
    public RestClient.Builder plainRestClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    public RestClient coreServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${core-service.read-timeout-ms:3000}") int readTimeout,
            BearerTokenClientInterceptor bearerTokenClientInterceptor) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(bearerTokenClientInterceptor)
                .build();
    }
}
