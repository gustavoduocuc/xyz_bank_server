package cl.duoc.xyzbank.authserver.clients.domain.repositories;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;

import java.util.List;
import java.util.Optional;

public interface ChannelClientRepository {

    Optional<ChannelClient> findByClientId(String clientId);

    List<ChannelClient> findAll();
}
