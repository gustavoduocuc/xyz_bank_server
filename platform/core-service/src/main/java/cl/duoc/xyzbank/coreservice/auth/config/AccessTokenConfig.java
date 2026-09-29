package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coreservice.auth.application.ports.AccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.AccessTokenDecoders;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters.JwtAccessTokenVerifier;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(AccessTokenProperties.class)
public class AccessTokenConfig {

    @Bean
    public AccessTokenVerifier accessTokenVerifier(AccessTokenProperties properties) {
        Map<String, Channel> channelsByClientId = properties.channelClients().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        return new JwtAccessTokenVerifier(
                AccessTokenDecoders.fromJwkSetUri(properties.jwkSetUri(), properties.issuer(), properties.audience()),
                channelsByClientId);
    }
}
