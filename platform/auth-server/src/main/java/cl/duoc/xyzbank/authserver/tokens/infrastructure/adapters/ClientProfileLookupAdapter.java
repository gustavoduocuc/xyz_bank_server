package cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.repositories.ServiceClientRepository;
import cl.duoc.xyzbank.authserver.tokens.application.dto.ClientProfile;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientProfileLookup;

import java.util.Optional;

public class ClientProfileLookupAdapter implements ClientProfileLookup {

    private final ChannelClientRepository channelClients;
    private final ServiceClientRepository serviceClients;

    public ClientProfileLookupAdapter(ChannelClientRepository channelClients, ServiceClientRepository serviceClients) {
        this.channelClients = channelClients;
        this.serviceClients = serviceClients;
    }

    @Override
    public Optional<ClientProfile> profileOf(String clientId) {
        return channelClients.findByClientId(clientId)
                .map(client -> new ClientProfile(client.channel(), client.audiences()))
                .or(() -> serviceClients.findByClientId(clientId)
                        .map(client -> new ClientProfile(client.channel(), client.audiences())));
    }
}
