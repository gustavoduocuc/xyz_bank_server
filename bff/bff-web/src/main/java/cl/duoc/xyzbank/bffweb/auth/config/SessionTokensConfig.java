package cl.duoc.xyzbank.bffweb.auth.config;

import cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters.AccessTokenCallerContextAdapter;
import cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.bffweb.auth.infrastructure.rest.RequestScopedAuthorizedClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * The authorization server's tokens are bff-web's session. Refresh reuses the same token
 * endpoint and client credentials as the login code exchange. The authorized-client
 * repository keeps a login's tokens only for the callback request, so they live afterward
 * only in the session cookies.
 */
@Configuration
public class SessionTokensConfig {

    @Bean
    public AccessTokenCallerContextAdapter accessTokenCallerContextAdapter(
            @Value("${spring.security.oauth2.client.provider.oidc.jwk-set-uri}") String jwkSetUri,
            @Value("${oidc.expected-issuer}") String issuer,
            @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId) {
        return new AccessTokenCallerContextAdapter(
                AccessTokenCallerContextAdapter.decoderFor(jwkSetUri, issuer), clientId);
    }

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new RequestScopedAuthorizedClientRepository();
    }

    /** The auth-server client, not the core-service one, which would add a bearer token. */
    @Bean
    public AuthServerTokenClient authServerTokenClient(
            @Value("${spring.security.oauth2.client.provider.oidc.token-uri}") String tokenUri,
            @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.oidc.client-secret}") String clientSecret,
            AuthServerHttpClients authServer) {
        return new AuthServerTokenClient(authServer.restClient(), tokenUri, clientId, clientSecret);
    }
}
