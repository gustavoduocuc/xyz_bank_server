package cl.duoc.xyzbank.authserver.clients.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Map;

/**
 * Translates a channel client into Spring Authorization Server's client model (design.md
 * Decision 3 of add-authorization-server). The registration id is the client id, so it is
 * stable across restarts; the only grant is authorization_code, PKCE is always required,
 * and consent is skipped because both clients are the bank's own apps.
 */
public class ChannelRegisteredClientMapper {

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

    private RegisteredClient.Builder authenticationOf(ChannelClient client, RegisteredClient.Builder builder) {
        if (!client.isConfidential()) {
            return builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(secretOf(client));
    }

    private String secretOf(ChannelClient client) {
        String secret = encodedSecretsByClientId.get(client.clientId());
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Confidential client " + client.clientId() + " has no secret configured");
        }
        return secret;
    }
}
