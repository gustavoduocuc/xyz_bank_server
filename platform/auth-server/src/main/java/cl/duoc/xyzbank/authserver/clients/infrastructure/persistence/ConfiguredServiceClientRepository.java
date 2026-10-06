package cl.duoc.xyzbank.authserver.clients.infrastructure.persistence;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ServiceClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;

import java.util.List;
import java.util.Optional;

/**
 * The fixed set of service clients (bff-atm, interests-service), built once from configuration.
 */
public class ConfiguredServiceClientRepository implements ServiceClientRepository {

    private final List<ServiceClient> clients;

    public ConfiguredServiceClientRepository(List<ServiceClient> clients) {
        this.clients = List.copyOf(clients);
    }

    @Override
    public Optional<ServiceClient> findByClientId(String clientId) {
        return clients.stream().filter(client -> client.hasClientId(clientId)).findFirst();
    }

    @Override
    public List<ServiceClient> findAll() {
        return clients;
    }
}
