package cl.duoc.xyzbank.authserver.clients.domain.valueobjects;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An OAuth client bound to exactly one channel. Its allowed scopes are derived from the
 * channel's scope set, never configured separately, so they cannot drift from Channel.
 */
public final class ChannelClient {

    private static final Set<String> OIDC_SCOPES = Set.of("openid", "profile");
    private static final Set<Channel> OAUTH_CHANNELS = Set.of(Channel.WEB, Channel.MOBILE);

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
        if (clientId == null || clientId.isBlank()) {
            throw DomainException.validation("Client id cannot be blank");
        }
        if (!OAUTH_CHANNELS.contains(channel)) {
            throw DomainException.validation("Only the web and mobile channels log in through OAuth, not " + channel);
        }
        if (redirectUri == null || !redirectUri.startsWith("https://")) {
            throw DomainException.validation("Redirect URI must use HTTPS: " + redirectUri);
        }
        return new ChannelClient(clientId, channel, type, redirectUri);
    }

    public String clientId() {
        return clientId;
    }

    public Channel channel() {
        return channel;
    }

    public boolean hasClientId(String candidateClientId) {
        return clientId.equals(candidateClientId);
    }

    public boolean isConfidential() {
        return type == ClientType.CONFIDENTIAL;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public Set<String> allowedScopes() {
        return Stream.concat(OIDC_SCOPES.stream(), channel.scopes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChannelClient client
                && clientId.equals(client.clientId)
                && channel == client.channel
                && type == client.type
                && redirectUri.equals(client.redirectUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, channel, type, redirectUri);
    }
}
