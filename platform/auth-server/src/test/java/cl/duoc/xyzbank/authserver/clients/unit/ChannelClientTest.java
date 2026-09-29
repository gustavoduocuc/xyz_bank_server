package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The ChannelClient")
class ChannelClientTest {

    /*
     * Cases:
     * 1. A web client allows exactly openid, profile and the web channel's scope set
     * 2. A mobile client allows exactly openid, profile and the mobile channel's scope set
     * 3. A web client and a mobile client share no channel scope
     * 4. Rejects a client for the ATM channel
     * 5. Rejects a client for the interests channel
     * 6. Rejects a blank client id
     * 7. Rejects a redirect URI that is not HTTPS
     * 8. Rejects a client without a redirect URI
     * 9. Equals another client with the same attributes
     * 10. Differs from a client with a different redirect URI
     * 11. A web client's tokens are meant for core-service and interests-service, and its refresh lasts 30 days
     * 12. A mobile client's tokens are meant for core-service only, and its refresh lasts 180 days
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";

    @Test
    @DisplayName("allows a web client exactly openid, profile and the web channel's scopes")
    void allowsAWebClientExactlyOpenidProfileAndTheWebChannelScopes() {
        Set<String> expected = new HashSet<>(Channel.WEB.scopes());
        expected.add("openid");
        expected.add("profile");

        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);

        assertEquals(expected, client.allowedScopes());
    }

    @Test
    @DisplayName("allows a mobile client exactly openid, profile and the mobile channel's scopes")
    void allowsAMobileClientExactlyOpenidProfileAndTheMobileChannelScopes() {
        Set<String> expected = new HashSet<>(Channel.MOBILE.scopes());
        expected.add("openid");
        expected.add("profile");

        ChannelClient client =
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

        assertEquals(expected, client.allowedScopes());
    }

    @Test
    @DisplayName("keeps web and mobile clients from sharing any channel scope")
    void keepsWebAndMobileClientsFromSharingAnyChannelScope() {
        ChannelClient web = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
        ChannelClient mobile =
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

        Set<String> shared = new HashSet<>(web.allowedScopes());
        shared.retainAll(mobile.allowedScopes());

        assertEquals(Set.of("openid", "profile"), shared);
    }

    @Test
    @DisplayName("rejects a client for the ATM channel")
    void rejectsAClientForTheAtmChannel() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> ChannelClient.create("bff-atm", Channel.ATM, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a client for the interests channel")
    void rejectsAClientForTheInterestsChannel() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> ChannelClient.create(
                        "interests-service", Channel.INTERESTS, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank client id")
    void rejectsABlankClientId() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> ChannelClient.create("  ", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a redirect URI that is not HTTPS")
    void rejectsARedirectUriThatIsNotHttps() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> ChannelClient.create(
                        "bff-web", Channel.WEB, ClientType.CONFIDENTIAL, "http://localhost:8081/login/oauth2/code/oidc"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a client without a redirect URI")
    void rejectsAClientWithoutARedirectUri() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, null));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("equals another client with the same attributes")
    void equalsAnotherClientWithTheSameAttributes() {
        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
        ChannelClient sameClient =
                ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);

        assertEquals(client, sameClient);
        assertEquals(client.hashCode(), sameClient.hashCode());
    }

    @Test
    @DisplayName("differs from a client with a different redirect URI")
    void differsFromAClientWithADifferentRedirectUri() {
        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
        ChannelClient otherClient =
                ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, MOBILE_REDIRECT_URI);

        assertNotEquals(client, otherClient);
    }

    @Test
    @DisplayName("aims web tokens at core-service and interests-service with a 30-day refresh")
    void aimsWebTokensAtCoreServiceAndInterestsServiceWithAThirtyDayRefresh() {
        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);

        assertEquals(Set.of("core-service", "interests-service"), client.audiences());
        assertEquals(Duration.ofDays(30), client.refreshTokenLifetime());
    }

    @Test
    @DisplayName("aims mobile tokens at core-service only with a 180-day refresh")
    void aimsMobileTokensAtCoreServiceOnlyWithAHundredEightyDayRefresh() {
        ChannelClient client =
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.CONFIDENTIAL, MOBILE_REDIRECT_URI);

        assertEquals(Set.of("core-service"), client.audiences());
        assertEquals(Duration.ofDays(180), client.refreshTokenLifetime());
    }
}
