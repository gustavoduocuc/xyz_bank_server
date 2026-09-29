package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
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

@DisplayName("The ChannelRegisteredClientMapper")
class ChannelRegisteredClientMapperTest {

    /*
     * Cases:
     * 1. Maps bff-web to a confidential, PKCE-only authorization-code client with its web scopes
     * 2. Maps bff-mobile to a public, PKCE-only authorization-code client with its mobile scopes
     * 3. Uses the client id as the registration id, so it is stable across restarts
     * 4. Refuses to map a confidential client whose secret is not configured
     *
     * (Lookup of stored clients -- unknown client, find by id -- is covered by
     * ChannelClientSeederIT against the JDBC client store.)
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";
    private static final ChannelClient WEB_CLIENT =
            ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
    private static final ChannelClient MOBILE_CLIENT =
            ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

    private final ChannelRegisteredClientMapper mapper =
            new ChannelRegisteredClientMapper(Map.of("bff-web", "{noop}web-secret"));

    @Test
    @DisplayName("maps bff-web to a confidential, PKCE-only authorization-code client with its web scopes")
    void mapsBffWebToAConfidentialPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = mapper.toRegisteredClient(WEB_CLIENT);

        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
        assertEquals("{noop}web-secret", client.getClientSecret());
        assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
        assertEquals(Set.of(WEB_REDIRECT_URI), client.getRedirectUris());
        assertEquals(WEB_CLIENT.allowedScopes(), client.getScopes());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
    }

    @Test
    @DisplayName("maps bff-mobile to a public, PKCE-only authorization-code client with its mobile scopes")
    void mapsBffMobileToAPublicPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = mapper.toRegisteredClient(MOBILE_CLIENT);

        assertEquals(Set.of(ClientAuthenticationMethod.NONE), client.getClientAuthenticationMethods());
        assertNull(client.getClientSecret());
        assertEquals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE), client.getAuthorizationGrantTypes());
        assertEquals(Set.of(MOBILE_REDIRECT_URI), client.getRedirectUris());
        assertEquals(MOBILE_CLIENT.allowedScopes(), client.getScopes());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
    }




    @Test
    @DisplayName("uses the client id as the registration id")
    void usesTheClientIdAsTheRegistrationId() {
        RegisteredClient client = mapper.toRegisteredClient(WEB_CLIENT);

        assertEquals("bff-web", client.getId());
        assertEquals("bff-web", client.getClientId());
    }

    @Test
    @DisplayName("refuses to map a confidential client whose secret is not configured")
    void refusesToMapAConfidentialClientWhoseSecretIsNotConfigured() {
        ChannelRegisteredClientMapper withoutSecrets = new ChannelRegisteredClientMapper(Map.of());

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> withoutSecrets.toRegisteredClient(WEB_CLIENT));

        assertTrue(exception.getMessage().contains("bff-web"), exception.getMessage());
    }
}
