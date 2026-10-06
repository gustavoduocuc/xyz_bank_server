package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * How interests-service deals with auth-server: validating incoming tokens (public issuer,
 * internal JWKS, its own audience, client of each channel) and obtaining its own token for
 * core-service (token URI, its client id and secret -- a client secret, not a signing key).
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        Map<Channel, String> channelClients,
        String tokenUri,
        String clientId,
        String clientSecret) {
}
