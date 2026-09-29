package cl.duoc.xyzbank.authserver.clients.integration;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelClientSeeder;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
import cl.duoc.xyzbank.authserver.clients.unit.InMemoryChannelClientRepository;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The ChannelClientSeeder")
class ChannelClientSeederIT extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Seeding stores exactly one registration per channel client, findable by client id and by id
     */

    private static final String SCHEMA = "seeder_it";
    private static final String WEB_REDIRECT_URI = "https://localhost:8081/login/oauth2/code/oidc";
    private static final String MOBILE_REDIRECT_URI = "https://localhost:8082/login/oauth2/code/oidc";
    private static final ChannelClient WEB_CLIENT =
            ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL, WEB_REDIRECT_URI);
    private static final ChannelClient MOBILE_CLIENT =
            ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC, MOBILE_REDIRECT_URI);

    private JdbcTemplate jdbcTemplate;
    private JdbcRegisteredClientRepository registeredClients;

    @BeforeEach
    void migrateAnEmptySchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + SCHEMA, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        registeredClients = new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("stores exactly one registration per channel client")
    void storesExactlyOneRegistrationPerChannelClient() {
        seederFor(Map.of("bff-web", "{noop}web-secret"), WEB_CLIENT, MOBILE_CLIENT).seed();

        assertEquals(Set.of("bff-web", "bff-mobile"), storedClientIds());
        RegisteredClient web = registeredClients.findByClientId("bff-web");
        assertNotNull(web);
        assertEquals(web.getClientId(), registeredClients.findById(web.getId()).getClientId());
        assertNull(registeredClients.findByClientId("bff-atm"));
    }

    private ChannelClientSeeder seederFor(Map<String, String> encodedSecrets, ChannelClient... clients) {
        return new ChannelClientSeeder(
                new InMemoryChannelClientRepository(clients),
                new ChannelRegisteredClientMapper(encodedSecrets),
                registeredClients);
    }

    private Set<String> storedClientIds() {
        return Set.copyOf(jdbcTemplate.queryForList("SELECT client_id FROM oauth2_registered_client", String.class));
    }
}
