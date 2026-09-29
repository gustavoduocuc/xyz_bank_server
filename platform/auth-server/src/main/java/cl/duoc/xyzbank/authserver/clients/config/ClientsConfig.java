package cl.duoc.xyzbank.authserver.clients.config;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
import cl.duoc.xyzbank.authserver.clients.infrastructure.persistence.ConfiguredChannelClientRepository;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
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
                        properties.mobile().clientId(), Channel.MOBILE, ClientType.PUBLIC,
                        properties.mobile().redirectUri())));
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            ChannelClientRepository channelClientRepository, AuthClientsProperties properties) {
        String encodedWebSecret =
                PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(properties.web().clientSecret());
        ChannelRegisteredClientMapper mapper =
                new ChannelRegisteredClientMapper(Map.of(properties.web().clientId(), encodedWebSecret));
        return new InMemoryRegisteredClientRepository(
                channelClientRepository.findAll().stream().map(mapper::toRegisteredClient).toList());
    }
}
