package cl.duoc.xyzbank.bffmobile.auth.config;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.RequestScopedAuthorizedClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

@Configuration
public class SessionTokensConfig {

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new RequestScopedAuthorizedClientRepository();
    }

    @Bean
    public AuthServerTokenClient authServerTokenClient(
            @Value("${spring.security.oauth2.client.provider.oidc.token-uri}") String tokenUri,
            @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.oidc.client-secret}") String clientSecret) {
        return new AuthServerTokenClient(RestClient.create(), tokenUri, clientId, clientSecret);
    }
}
