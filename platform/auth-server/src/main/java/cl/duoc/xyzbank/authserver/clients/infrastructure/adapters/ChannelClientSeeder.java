package cl.duoc.xyzbank.authserver.clients.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Writes every channel client into the persistent client store (design.md Decision 3). The
 * store's save inserts a client it does not know and updates one it does (same registration
 * id), so seeding on every startup is idempotent and applies configuration changes.
 */
public class ChannelClientSeeder {

    private final ChannelClientRepository channelClients;
    private final ChannelRegisteredClientMapper mapper;
    private final RegisteredClientRepository registeredClients;

    public ChannelClientSeeder(
            ChannelClientRepository channelClients,
            ChannelRegisteredClientMapper mapper,
            RegisteredClientRepository registeredClients) {
        this.channelClients = channelClients;
        this.mapper = mapper;
        this.registeredClients = registeredClients;
    }

    public void seed() {
        channelClients.findAll().stream().map(mapper::toRegisteredClient).forEach(registeredClients::save);
    }
}
