package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ServiceClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryServiceClientRepository implements ServiceClientRepository {

    private final Map<String, ServiceClient> clients = new ConcurrentHashMap<>();

    public InMemoryServiceClientRepository(ServiceClient... clients) {
        for (ServiceClient client : clients) {
            this.clients.put(client.clientId(), client);
        }
    }

    @Override
    public Optional<ServiceClient> findByClientId(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    @Override
    public List<ServiceClient> findAll() {
        return List.copyOf(clients.values());
    }
}
