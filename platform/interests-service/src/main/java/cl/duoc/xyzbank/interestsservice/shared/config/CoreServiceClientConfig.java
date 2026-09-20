package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest.BearerTokenClientInterceptor;
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
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false")
    public RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient coreServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${core-service.read-timeout-ms:3000}") int readTimeout,
            @Value("${core-service.service-credential}") String serviceCredential,
            BearerTokenClientInterceptor bearerTokenClientInterceptor) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Service-Credential", serviceCredential)
                .requestInterceptor(bearerTokenClientInterceptor)
                .build();
    }
}
