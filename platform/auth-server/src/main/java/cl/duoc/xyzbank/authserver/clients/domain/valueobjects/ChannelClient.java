package cl.duoc.xyzbank.authserver.clients.domain.valueobjects;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.HashSet;
import java.util.Set;

/**
 * An OAuth client bound to exactly one channel. Its allowed scopes are derived from the
 * channel's scope set, never configured separately, so they cannot drift from Channel.
 */
public final class ChannelClient {

    private static final Set<String> OIDC_SCOPES = Set.of("openid", "profile");

    private final String clientId;
    private final Channel channel;
    private final ClientType type;
    private final String redirectUri;

    private ChannelClient(String clientId, Channel channel, ClientType type, String redirectUri) {
        this.clientId = clientId;
        this.channel = channel;
        this.type = type;
        this.redirectUri = redirectUri;
    }

    public static ChannelClient create(String clientId, Channel channel, ClientType type, String redirectUri) {
        return new ChannelClient(clientId, channel, type, redirectUri);
    }

    public Set<String> allowedScopes() {
        Set<String> scopes = new HashSet<>(OIDC_SCOPES);
        scopes.addAll(channel.scopes());
        return Set.copyOf(scopes);
    }
}
