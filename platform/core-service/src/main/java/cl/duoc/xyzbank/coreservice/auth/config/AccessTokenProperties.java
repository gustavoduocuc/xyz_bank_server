package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * How core-service recognises auth-server's access tokens: auth-server's public issuer, the
 * internal JWKS URI, core-service's own audience name, and which client id each channel's
 * tokens must be issued to.
 */
@ConfigurationProperties(prefix = "auth")
public record AccessTokenProperties(String issuer, String jwkSetUri, String audience, Map<Channel, String> channelClients) {
}
