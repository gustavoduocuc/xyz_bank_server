package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ChannelRegisteredClientMapper")
class ChannelRegisteredClientMapperTest {

    /*
     * Cases:
     * 1. Maps bff-web to a confidential, PKCE-only authorization-code client with its web scopes
     *    and 30-day rotating refresh tokens
     * 2. Maps bff-mobile to a confidential, PKCE-only authorization-code client with its mobile
     *    scopes and 180-day rotating refresh tokens
     * 3. Uses the client id as the registration id, so it is stable across restarts
     * 4. Refuses to map a confidential client whose secret is not configured
     * 5. Maps a service client to a client_credentials-only client with its channel's scopes
     * 6. Gives every client 15-minute access tokens
     *
     * (Lookup of stored clients -- unknown client, find by id -- is covered by
     * ChannelClientSeederIT against the JDBC client store.)
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";
    private static final ChannelClient WEB_CLIENT =
            ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
    private static final ChannelClient MOBILE_CLIENT =
            ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.CONFIDENTIAL, MOBILE_REDIRECT_URI);
    private static final ServiceClient ATM_CLIENT = ServiceClient.create("bff-atm", Channel.ATM);

    private final ChannelRegisteredClientMapper mapper = new ChannelRegisteredClientMapper(Map.of(
            "bff-web", "{noop}web-secret", "bff-mobile", "{noop}mobile-secret", "bff-atm", "{noop}atm-secret"));

    @Test
    @DisplayName("maps bff-web to a confidential, PKCE-only authorization-code client with its web scopes")
    void mapsBffWebToAConfidentialPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = mapper.toRegisteredClient(WEB_CLIENT);

        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
        assertEquals("{noop}web-secret", client.getClientSecret());
        assertEquals(
                Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN),
                client.getAuthorizationGrantTypes());
        assertEquals(Set.of(WEB_REDIRECT_URI), client.getRedirectUris());
        assertEquals(WEB_CLIENT.allowedScopes(), client.getScopes());
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertFalse(client.getClientSettings().isRequireAuthorizationConsent());
        assertEquals(Duration.ofDays(30), client.getTokenSettings().getRefreshTokenTimeToLive());
        assertFalse(client.getTokenSettings().isReuseRefreshTokens());
    }

    @Test
    @DisplayName("maps bff-mobile to a confidential, PKCE-only authorization-code client with its mobile scopes")
    void mapsBffMobileToAConfidentialPkceOnlyAuthorizationCodeClient() {
        RegisteredClient client = mapper.toRegisteredClient(MOBILE_CLIENT);

        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
        assertEquals("{noop}mobile-secret", client.getClientSecret());
        assertEquals(
                Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN),
                client.getAuthorizationGrantTypes());
        assertEquals(Duration.ofDays(180), client.getTokenSettings().getRefreshTokenTimeToLive());
        assertFalse(client.getTokenSettings().isReuseRefreshTokens());
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

    @Test
    @DisplayName("maps a service client to a client_credentials-only client with its channel's scopes")
    void mapsAServiceClientToAClientCredentialsOnlyClient() {
        RegisteredClient client = mapper.toRegisteredClient(ATM_CLIENT);

        assertEquals("bff-atm", client.getId());
        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC), client.getClientAuthenticationMethods());
        assertEquals("{noop}atm-secret", client.getClientSecret());
        assertEquals(Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS), client.getAuthorizationGrantTypes());
        assertEquals(Set.of("atm:read-balance", "atm:withdraw"), client.getScopes());
        assertTrue(client.getRedirectUris().isEmpty());
    }

    @Test
    @DisplayName("gives every client 15-minute access tokens")
    void givesEveryClient15MinuteAccessTokens() {
        Duration fifteenMinutes = Duration.ofMinutes(15);

        assertEquals(fifteenMinutes, mapper.toRegisteredClient(WEB_CLIENT).getTokenSettings().getAccessTokenTimeToLive());
        assertEquals(fifteenMinutes, mapper.toRegisteredClient(MOBILE_CLIENT).getTokenSettings().getAccessTokenTimeToLive());
        assertEquals(fifteenMinutes, mapper.toRegisteredClient(ATM_CLIENT).getTokenSettings().getAccessTokenTimeToLive());
    }
}
