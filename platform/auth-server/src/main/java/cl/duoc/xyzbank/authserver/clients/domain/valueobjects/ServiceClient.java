package cl.duoc.xyzbank.authserver.clients.domain.valueobjects;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A client that obtains tokens for itself (client_credentials), not for a logged-in
 * customer: bff-atm and interests-service. Its scopes are its channel's scope set.
 */
public final class ServiceClient {

    private static final Set<Channel> SERVICE_CHANNELS = Set.of(Channel.ATM, Channel.INTERESTS);
    // interests-service's own token also reaches interests-service itself (the interest
    // application trigger requires interests:write); bff-atm's only reaches core-service
    private static final Map<Channel, Set<String>> AUDIENCES = Map.of(
            Channel.ATM, Set.of("core-service"),
            Channel.INTERESTS, Set.of("core-service", "interests-service"));

    private final String clientId;
    private final Channel channel;

    private ServiceClient(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
    }

    public static ServiceClient create(String clientId, Channel channel) {
        if (clientId == null || clientId.isBlank()) {
            throw DomainException.validation("Client id cannot be blank");
        }
        if (!SERVICE_CHANNELS.contains(channel)) {
            throw DomainException.validation("Only the ATM and interests channels use service clients, not " + channel);
        }
        return new ServiceClient(clientId, channel);
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

    public Set<String> allowedScopes() {
        return Set.copyOf(channel.scopes());
    }

    public Set<String> audiences() {
        return AUDIENCES.get(channel);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ServiceClient client && clientId.equals(client.clientId) && channel == client.channel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, channel);
    }
}
