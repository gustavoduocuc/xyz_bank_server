package cl.duoc.xyzbank.authserver.clients.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Map;

/**
 * Exposes the channel clients to Spring Authorization Server (design.md Decision 3). The
 * registration id is the client id, the only grant is authorization_code, PKCE is always
 * required, and consent is skipped because both clients are the bank's own apps. Read-only:
 * clients come from the channel model, never from runtime registration.
 */
public class ChannelRegisteredClientRepository implements RegisteredClientRepository {

    private final ChannelClientRepository channelClients;
    private final Map<String, String> encodedSecretsByClientId;

    public ChannelRegisteredClientRepository(
            ChannelClientRepository channelClients, Map<String, String> encodedSecretsByClientId) {
        this.channelClients = channelClients;
        this.encodedSecretsByClientId = Map.copyOf(encodedSecretsByClientId);
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Channel clients cannot be registered at runtime");
    }

    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return channelClients.findByClientId(clientId).map(this::toRegisteredClient).orElse(null);
    }

    private RegisteredClient toRegisteredClient(ChannelClient client) {
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
