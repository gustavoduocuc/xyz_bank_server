package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.persistence.ConfiguredChannelClientRepository;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The ConfiguredChannelClientRepository")
class ConfiguredChannelClientRepositoryTest {

    /*
     * Cases:
     * 1. Finds each configured client by its client id
     * 2. Knows no client that was not configured
     * 3. Keeps the clients it was configured with, even if the source list changes later
     */

    private static final ChannelClient WEB_CLIENT = ChannelClient.create(
            "bff-web", Channel.WEB, ClientType.CONFIDENTIAL, "https://localhost:8081/login/oauth2/code/oidc");
    private static final ChannelClient MOBILE_CLIENT = ChannelClient.create(
            "bff-mobile", Channel.MOBILE, ClientType.PUBLIC, "https://localhost:8082/login/oauth2/code/oidc");

    private final ConfiguredChannelClientRepository repository =
            new ConfiguredChannelClientRepository(List.of(WEB_CLIENT, MOBILE_CLIENT));

    @Test
    @DisplayName("finds each configured client by its client id")
    void findsEachConfiguredClientByItsClientId() {
        Optional<ChannelClient> web = repository.findByClientId("bff-web");
        Optional<ChannelClient> mobile = repository.findByClientId("bff-mobile");

        assertEquals(Optional.of(WEB_CLIENT), web);
        assertEquals(Optional.of(MOBILE_CLIENT), mobile);
    }

    @Test
    @DisplayName("knows no client that was not configured")
    void knowsNoClientThatWasNotConfigured() {
        Optional<ChannelClient> client = repository.findByClientId("bff-atm");

        assertTrue(client.isEmpty());
    }

    @Test
    @DisplayName("keeps the clients it was configured with even if the source list changes later")
    void keepsTheClientsItWasConfiguredWith() {
        List<ChannelClient> source = new ArrayList<>(List.of(WEB_CLIENT));
        ConfiguredChannelClientRepository configured = new ConfiguredChannelClientRepository(source);

        source.add(MOBILE_CLIENT);

        assertEquals(List.of(WEB_CLIENT), configured.findAll());
        assertTrue(configured.findByClientId("bff-mobile").isEmpty());
    }
}
