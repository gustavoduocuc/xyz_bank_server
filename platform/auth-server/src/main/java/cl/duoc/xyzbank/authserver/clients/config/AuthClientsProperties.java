package cl.duoc.xyzbank.authserver.clients.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The deployment-specific parts of the two channel clients. The channel rules (which
 * channels, which scopes, HTTPS redirects) live in ChannelClient; the protocol policy
 * (authorization_code only, PKCE, no consent) in ChannelRegisteredClientRepository.
 */
@ConfigurationProperties(prefix = "auth.clients")
public record AuthClientsProperties(WebClient web, MobileClient mobile) {

    public record WebClient(String clientId, String clientSecret, String redirectUri) {
    }

    public record MobileClient(String clientId, String redirectUri) {
    }
}
