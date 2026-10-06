package cl.duoc.xyzbank.authserver.clients.config;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.repositories.ServiceClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelClientSeeder;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
import cl.duoc.xyzbank.authserver.clients.infrastructure.persistence.ConfiguredChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.infrastructure.persistence.ConfiguredServiceClientRepository;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;
import java.util.Map;

@Configuration
public class ClientsConfig {

    @Bean
    public ChannelClientRepository channelClientRepository(AuthClientsProperties properties) {
        return new ConfiguredChannelClientRepository(List.of(
                ChannelClient.create(
                        properties.web().clientId(), Channel.WEB, ClientType.CONFIDENTIAL,
                        properties.web().redirectUri()),
                ChannelClient.create(
                        properties.mobile().clientId(), Channel.MOBILE, ClientType.CONFIDENTIAL,
                        properties.mobile().redirectUri())));
    }

    @Bean
    public ServiceClientRepository serviceClientRepository(AuthClientsProperties properties) {
        return new ConfiguredServiceClientRepository(List.of(
                ServiceClient.create(properties.atm().clientId(), Channel.ATM),
                ServiceClient.create(properties.interests().clientId(), Channel.INTERESTS)));
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * Seeds the persistent client store once every singleton exists and before the web
     * server starts accepting requests, so no request ever sees a missing or stale client.
     */
    @Bean
    public SmartInitializingSingleton channelClientSeeding(
            ChannelClientRepository channelClientRepository,
            ServiceClientRepository serviceClientRepository,
            RegisteredClientRepository registeredClientRepository,
            AuthClientsProperties properties) {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        ChannelRegisteredClientMapper mapper = new ChannelRegisteredClientMapper(Map.of(
                properties.web().clientId(), encoder.encode(properties.web().clientSecret()),
                properties.mobile().clientId(), encoder.encode(properties.mobile().clientSecret()),
                properties.atm().clientId(), encoder.encode(properties.atm().clientSecret()),
                properties.interests().clientId(), encoder.encode(properties.interests().clientSecret())));
        ChannelClientSeeder seeder = new ChannelClientSeeder(
                channelClientRepository, serviceClientRepository, mapper, registeredClientRepository);
        return seeder::seed;
    }
}
