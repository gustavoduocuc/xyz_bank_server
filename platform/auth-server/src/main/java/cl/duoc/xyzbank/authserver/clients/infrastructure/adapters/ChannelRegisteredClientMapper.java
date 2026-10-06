package cl.duoc.xyzbank.authserver.clients.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Map;

/**
 * Translates the clients of the channel model into Spring Authorization Server's client
 * model. The registration id is the client id, so it is stable across restarts.
 *
 * <p>Channel clients log customers in: authorization_code with PKCE always required, no
 * consent (the bank's own apps), and refresh tokens that rotate on every use with the
 * channel's lifetime. Service clients obtain tokens for themselves: client_credentials only.
 * Every client gets 15-minute access tokens (adopt-oauth2-tokens-between-services design.md
 * Decisions 2 and 3).
 */
public class ChannelRegisteredClientMapper {

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final Map<String, String> encodedSecretsByClientId;

    public ChannelRegisteredClientMapper(Map<String, String> encodedSecretsByClientId) {
        this.encodedSecretsByClientId = Map.copyOf(encodedSecretsByClientId);
    }

    public RegisteredClient toRegisteredClient(ChannelClient client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.clientId())
                .clientId(client.clientId())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(client.redirectUri())
                .scopes(scopes -> scopes.addAll(client.allowedScopes()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build());
        return authenticationOf(client, builder).build();
    }

    public RegisteredClient toRegisteredClient(ServiceClient client) {
        return RegisteredClient.withId(client.clientId())
                .clientId(client.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(secretOf(client.clientId()))
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(scopes -> scopes.addAll(client.allowedScopes()))
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME).build())
                .build();
    }

    private RegisteredClient.Builder authenticationOf(ChannelClient client, RegisteredClient.Builder builder) {
        if (!client.isConfidential()) {
            // Spring Authorization Server issues no refresh token to public clients
            return builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME).build());
        }
        return builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(secretOf(client.clientId()))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                        .refreshTokenTimeToLive(client.refreshTokenLifetime())
                        .reuseRefreshTokens(false)
                        .build());
    }

    private String secretOf(String clientId) {
        String secret = encodedSecretsByClientId.get(clientId);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Confidential client " + clientId + " has no secret configured");
        }
        return secret;
    }
}
