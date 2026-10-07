package cl.duoc.xyzbank.authserver.clients.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The deployment-specific parts of the five clients: ids, secrets and redirect URIs. The
 * channel rules (which channels, which scopes, audiences, lifetimes, HTTPS redirects) live in
 * ChannelClient and ServiceClient; the protocol policy (grants, PKCE, no consent, rotation) in
 * ChannelRegisteredClientMapper.
 */
@ConfigurationProperties(prefix = "auth.clients")
public record AuthClientsProperties(
        LoginClient web,
        LoginClient mobile,
        ServiceClientSettings atm,
        ServiceClientSettings interests,
        ServiceClientSettings customersAdmin) {

    public record LoginClient(String clientId, String clientSecret, String redirectUri) {
    }

    public record ServiceClientSettings(String clientId, String clientSecret) {
    }
}
