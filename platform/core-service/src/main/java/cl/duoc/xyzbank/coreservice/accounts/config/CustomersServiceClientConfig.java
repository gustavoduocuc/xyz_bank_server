package cl.duoc.xyzbank.coreservice.accounts.config;

import cl.duoc.xyzbank.coreservice.shared.infrastructure.rest.AuthorizationForwardingInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The client core-service uses to ask customers-service whether a customer exists. With
 * Eureka enabled the base URL is the service id ({@code http://customers-service}) and the
 * builder is load-balanced; without it (tests, local runs) the base URL is a plain address.
 */
@Configuration
public class CustomersServiceClientConfig {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true")
    public RestClient.Builder loadBalancedCustomersServiceClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false", matchIfMissing = true)
    public RestClient.Builder plainCustomersServiceClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    @Qualifier("customersServiceClient")
    public RestClient customersServiceClient(
            RestClient.Builder customersServiceClientBuilder,
            @Value("${customers-service.base-url}") String baseUrl,
            @Value("${customers-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${customers-service.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return customersServiceClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                // The accounts-admin token that opened the request also carries customers:read
                .requestInterceptor(new AuthorizationForwardingInterceptor())
                .build();
    }
}
