package cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientChannelLookup;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Optional;

public class ChannelClientLookupAdapter implements ClientChannelLookup {

    private final ChannelClientRepository channelClients;

    public ChannelClientLookupAdapter(ChannelClientRepository channelClients) {
        this.channelClients = channelClients;
    }

    @Override
    public Optional<Channel> channelOf(String clientId) {
        return channelClients.findByClientId(clientId).map(ChannelClient::channel);
    }
}
