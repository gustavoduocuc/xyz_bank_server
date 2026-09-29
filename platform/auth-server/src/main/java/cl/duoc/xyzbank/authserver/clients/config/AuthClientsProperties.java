package cl.duoc.xyzbank.authserver.clients.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The deployment-specific parts of the two channel clients. Everything that is a rule (grant
 * type, PKCE, scopes, client type) lives in the channel model instead.
 */
@ConfigurationProperties(prefix = "auth.clients")
public record AuthClientsProperties(WebClient web, MobileClient mobile) {

    public record WebClient(String clientId, String clientSecret, String redirectUri) {
    }

    public record MobileClient(String clientId, String redirectUri) {
    }
}
