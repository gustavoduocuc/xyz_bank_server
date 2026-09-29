package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientRepository;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ChannelRegisteredClientRepository")
class ChannelRegisteredClientRepositoryTest {

    /*
     * Cases:
     * 1. Registers bff-web as a confidential, PKCE-only authorization-code client with its web scopes
     * 2. Registers bff-mobile as a public, PKCE-only authorization-code client with its mobile scopes
     * 3. Knows no other client
     * 4. Finds a client by its registration id as well as its client id
     * 5. Refuses to register clients at runtime
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";
    private static final ChannelClient WEB_CLIENT =
            ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
    private static final ChannelClient MOBILE_CLIENT =
            ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

    private final ChannelRegisteredClientRepository repository = new ChannelRegisteredClientRepository(
            new InMemoryChannelClientRepository(WEB_CLIENT, MOBILE_CLIENT), Map.of("bff-web", "{noop}web-secret"));

    @Test
    @DisplayName("registers bff-web as a confidential, PKCE-only authorization-code client with its web scopes")
    void registersBffWebAsAConfidentialPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = repository.findByClientId("bff-web");

        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
        assertEquals("{noop}web-secret", client.getClientSecret());
        assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
        assertEquals(Set.of(WEB_REDIRECT_URI), client.getRedirectUris());
        assertEquals(WEB_CLIENT.allowedScopes(), client.getScopes());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
    }

    @Test
    @DisplayName("registers bff-mobile as a public, PKCE-only authorization-code client with its mobile scopes")
    void registersBffMobileAsAPublicPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = repository.findByClientId("bff-mobile");

        assertEquals(Set.of(ClientAuthenticationMethod.NONE), client.getClientAuthenticationMethods());
        assertNull(client.getClientSecret());
        assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
        assertEquals(Set.of(MOBILE_REDIRECT_URI), client.getRedirectUris());
        assertEquals(MOBILE_CLIENT.allowedScopes(), client.getScopes());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
    }

    @Test
    @DisplayName("knows no client other than the channel clients")
    void knowsNoClientOtherThanTheChannelClients() {
        assertNull(repository.findByClientId("bff-atm"));
        assertNull(repository.findById("bff-atm"));
    }

    @Test
    @DisplayName("finds a client by its registration id")
    void findsAClientByItsRegistrationId() {
        RegisteredClient byClientId = repository.findByClientId("bff-web");

        assertEquals(byClientId, repository.findById(byClientId.getId()));
    }

    @Test
    @DisplayName("refuses to register clients at runtime")
    void refusesToRegisterClientsAtRuntime() {
        RegisteredClient client = repository.findByClientId("bff-web");

        assertThrows(UnsupportedOperationException.class, () -> repository.save(client));
    }
}
