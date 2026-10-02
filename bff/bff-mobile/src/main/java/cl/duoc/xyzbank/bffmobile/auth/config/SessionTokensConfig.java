package cl.duoc.xyzbank.bffmobile.auth.config;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AccessTokenCallerContextAdapter;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.RequestScopedAuthorizedClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration
public class SessionTokensConfig {

    @Bean
    public AccessTokenCallerContextAdapter accessTokenCallerContextAdapter(
            @Value("${spring.security.oauth2.client.provider.oidc.jwk-set-uri}") String jwkSetUri,
            @Value("${oidc.expected-issuer}") String issuer,
            @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId,
            AuthServerHttpClients authServer) {
        return new AccessTokenCallerContextAdapter(
                AccessTokenCallerContextAdapter.decoderFor(jwkSetUri, issuer, authServer.restOperations()), clientId);
    }

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new RequestScopedAuthorizedClientRepository();
    }

    @Bean
    public AuthServerTokenClient authServerTokenClient(
            @Value("${spring.security.oauth2.client.provider.oidc.token-uri}") String tokenUri,
            @Value("${auth-server.base-url}") String revocationBaseUri,
            @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.oidc.client-secret}") String clientSecret,
            AuthServerHttpClients authServer) {
        return new AuthServerTokenClient(authServer.restClient(), tokenUri, revocationBaseUri, clientId, clientSecret);
    }
}
