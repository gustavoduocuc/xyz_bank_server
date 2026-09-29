package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The ChannelClient")
class ChannelClientTest {

    /*
     * Cases:
     * 1. A web client allows exactly openid, profile and the web channel's scope set
     */

    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";

    @Test
    @DisplayName("allows a web client exactly openid, profile and the web channel's scopes")
    void allowsAWebClientExactlyOpenidProfileAndTheWebChannelScopes() {
        ChannelClient client = ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);

        Set<String> expected = new HashSet<>(Channel.WEB.scopes());
        expected.add("openid");
        expected.add("profile");
        assertEquals(expected, client.allowedScopes());
    }
}
