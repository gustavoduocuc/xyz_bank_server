package cl.duoc.xyzbank.authserver.clients.infrastructure.persistence;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;

import java.util.List;
import java.util.Optional;

/**
 * The fixed set of channel clients, built once from configuration at startup.
 */
public class ConfiguredChannelClientRepository implements ChannelClientRepository {

    private final List<ChannelClient> clients;

    public ConfiguredChannelClientRepository(List<ChannelClient> clients) {
        this.clients = List.copyOf(clients);
    }

    @Override
    public Optional<ChannelClient> findByClientId(String clientId) {
        return clients.stream().filter(client -> client.hasClientId(clientId)).findFirst();
    }

    @Override
    public List<ChannelClient> findAll() {
        return clients;
    }
}
