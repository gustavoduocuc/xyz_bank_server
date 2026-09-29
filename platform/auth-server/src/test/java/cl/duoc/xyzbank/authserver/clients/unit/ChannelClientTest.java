package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";

    @Test
    @DisplayName("allows a web client exactly openid, profile and the web channel's scopes")
    void allowsAWebClientExactlyOpenidProfileAndTheWebChannelScopes() {
        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);

        Set<String> expected = new HashSet<>(Channel.WEB.scopes());
        expected.add("openid");
        expected.add("profile");
        assertEquals(expected, client.allowedScopes());
    }

    @Test
    @DisplayName("allows a mobile client exactly openid, profile and the mobile channel's scopes")
    void allowsAMobileClientExactlyOpenidProfileAndTheMobileChannelScopes() {
        ChannelClient client =
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

        Set<String> expected = new HashSet<>(Channel.MOBILE.scopes());
        expected.add("openid");
        expected.add("profile");
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
}
