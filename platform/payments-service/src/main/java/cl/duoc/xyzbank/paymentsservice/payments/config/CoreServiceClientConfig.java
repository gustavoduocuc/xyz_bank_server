package cl.duoc.xyzbank.paymentsservice.payments.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.web.client.RestClient;

/**
 * The client payments-service posts a payment's entries with. With Eureka enabled the base URL is
 * the service id ({@code http://core-service}) and the builder is load-balanced; without it (tests,
 * local runs) the base URL is a plain address. Every call carries payments-service's own
 * client_credentials token, obtained once and reused until it expires.
 */
@Configuration
public class CoreServiceClientConfig {

    private static final String REGISTRATION_ID = "core-service";

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true")
    public RestClient.Builder loadBalancedCoreServiceClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false", matchIfMissing = true)
    public RestClient.Builder plainCoreServiceClientBuilder() {
        return RestClient.builder();
    }

    // Works outside a servlet request: the token belongs to payments-service, not to the caller
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    @Qualifier("coreServiceClient")
    public RestClient coreServiceClient(
            RestClient.Builder coreServiceClientBuilder,
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        OAuth2ClientHttpRequestInterceptor tokenInterceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        tokenInterceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        // One token for the application, not one per caller
        tokenInterceptor.setPrincipalResolver(new RequestAttributePrincipalResolver());
        return coreServiceClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(tokenInterceptor)
                .build();
    }
}
