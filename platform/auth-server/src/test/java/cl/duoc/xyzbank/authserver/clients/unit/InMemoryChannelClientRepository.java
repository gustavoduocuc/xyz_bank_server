package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChannelClientRepository implements ChannelClientRepository {

    private final Map<String, ChannelClient> clients = new ConcurrentHashMap<>();

    public InMemoryChannelClientRepository(ChannelClient... clients) {
        for (ChannelClient client : clients) {
            this.clients.put(client.clientId(), client);
        }
    }

    @Override
    public Optional<ChannelClient> findByClientId(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    @Override
    public List<ChannelClient> findAll() {
        return List.copyOf(clients.values());
    }
}
