package cl.duoc.xyzbank.authserver.clients.domain.repositories;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;

import java.util.List;
import java.util.Optional;

public interface ServiceClientRepository {

    Optional<ServiceClient> findByClientId(String clientId);

    List<ServiceClient> findAll();
}
